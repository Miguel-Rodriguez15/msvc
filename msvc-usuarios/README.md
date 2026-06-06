# msvc-usuarios — Microservicio de Usuarios

Microservicio encargado de la gestión de usuarios. Actúa como **OAuth2 Resource Server**, valida tokens JWT emitidos por `msvc-auth` y protege sus endpoints con scopes. Usa MySQL 8 como base de datos y OpenFeign para comunicarse con `msvc-cursos`.

---

## Tabla de Contenidos

- [Responsabilidad](#responsabilidad)
- [Tecnologías](#tecnologías)
- [Configuración](#configuración)
- [Modelo de datos](#modelo-de-datos)
- [API Endpoints](#api-endpoints)
- [Seguridad](#seguridad)
- [Comunicación entre microservicios](#comunicación-entre-microservicios)
- [Observabilidad](#observabilidad)
- [Ejecución local](#ejecución-local)
- [Docker](#docker)
- [Kubernetes](#kubernetes)

---

## Responsabilidad

- CRUD completo de usuarios
- Validación de email único en creación y edición
- Encriptación de contraseñas con BCrypt
- Servir credenciales a `msvc-auth` para el flujo OAuth2
- Notificar a `msvc-cursos` al eliminar un usuario (limpieza de inscripciones)
- Consultas de usuarios por lista de IDs (usado por `msvc-cursos`)

---

## Tecnologías

| Dependencia | Versión | Propósito |
|---|---|---|
| Spring Boot | 3.5.0 | Framework base |
| Spring Data JPA | integrado | ORM con Hibernate |
| Spring Security 6 | integrado | Resource Server OAuth2 |
| Spring Cloud OpenFeign | 2025.0.0 | Cliente HTTP declarativo |
| Spring Cloud Kubernetes | 2025.0.0 | Descubrimiento de servicios |
| Spring Actuator | integrado | Health probes y métricas |
| MySQL Connector/J | integrado | Driver JDBC |
| Logstash Logback Encoder | 7.4 | Logging estructurado a ELK |

---

## Configuración

### `application.properties`

```properties
spring.application.name=msvc-usuarios
server.port=${PORT:8001}

# Base de datos MySQL
spring.datasource.url=jdbc:mysql://${DB_HOT:mysql8:3306}/${DB_DATABASE:msvc_usuarios}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:admin123}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.generate-ddl=true

# Kubernetes Config Discovery
spring.cloud.kubernetes.config.name=msvc-usuarios-config
spring.cloud.kubernetes.config.namespace=default
spring.config.import=optional:kubernetes:
spring.profiles.active=dev

# Actuator (health probes para K8s)
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

### `application.yml` (OAuth2)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${LB_AUTH_ISSUER_URI:http://127.0.0.1:9000}
      client:
        registration:
          msvc-usuarios-client:
            client-id: usuarios-client
            client-secret: 12345
            authorization-grant-type: authorization_code
            scope: openid, read, write
        provider:
          spring:
            issuer-uri: ${LB_AUTH_ISSUER_URI:http://127.0.0.1:9000}
```

### Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto del servidor | `8001` |
| `DB_HOT` | Host:puerto de MySQL | `mysql8:3306` |
| `DB_DATABASE` | Nombre de base de datos | `msvc_usuarios` |
| `DB_USERNAME` | Usuario MySQL | `root` |
| `DB_PASSWORD` | Contraseña MySQL | `admin123` |
| `CURSOS_URL` | URL de msvc-cursos (Feign) | `msvc-cursos:8002` |
| `LB_AUTH_ISSUER_URI` | Issuer URI de msvc-auth | `http://127.0.0.1:9000` |
| `LB_AUTH_REDIRECT_URI` | Redirect URI OAuth2 | — |
| `SPRING_PROFILES_ACTIVE` | Perfiles activos | `dev` |
| `LOGSTASH_HOST` | IP del servidor Logstash | `localhost` |

---

## Modelo de datos

### Entidad `Usuario`

```java
@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Column(unique = true)
    private String email;

    private String password;  // BCrypt hash
}
```

**Base de datos:** `msvc_usuarios` en MySQL 8  
**Tabla:** `usuarios`  
**Schema:** generado automáticamente por Hibernate (`generate-ddl=true`)

---

## API Endpoints

### Usuarios

| Método | Ruta | Descripción | Scope |
|---|---|---|---|
| GET | `/` | Listar todos los usuarios | `SCOPE_read` |
| GET | `/{id}` | Obtener usuario por ID | `SCOPE_read` |
| POST | `/` | Crear nuevo usuario | `SCOPE_write` |
| PUT | `/{id}` | Editar usuario existente | `SCOPE_write` |
| DELETE | `/{id}` | Eliminar usuario | `SCOPE_write` |
| GET | `/usuarios-por-curso?ids=1,2,3` | Lista de usuarios por IDs | `SCOPE_read` |

### Autenticación y uso interno

| Método | Ruta | Descripción | Scope |
|---|---|---|---|
| GET | `/login?email=x@x.com` | Buscar usuario por email (usado por msvc-auth) | público |
| GET | `/oauth2/authorization/msvc-usuarios-client` | Iniciar flujo OAuth2 | público |
| GET | `/authorized?code=...` | Callback del servidor de autorización | público |

### Respuesta de `GET /`

```json
{
  "usuarios": [...],
  "pod_info": "pod-name / pod-ip",
  "configuracion": "..."
}
```

### Ejemplo de creación (`POST /`)

```json
{
  "nombre": "Juan Pérez",
  "email": "juan@email.com",
  "password": "secreta123"
}
```

La contraseña se hashea con BCrypt antes de persistir. Si el email ya existe, retorna `400 Bad Request`.

---

## Seguridad

### `SecurityConfig.java`

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/authorized", "/login", "/oauth2/**", "/actuator/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/", "/{id}", "/usuarios-por-curso").hasAnyAuthority("SCOPE_read", "SCOPE_write")
            .requestMatchers(HttpMethod.POST, "/").hasAuthority("SCOPE_write")
            .requestMatchers(HttpMethod.PUT, "/{id}").hasAuthority("SCOPE_write")
            .requestMatchers(HttpMethod.DELETE, "/{id}").hasAuthority("SCOPE_write")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .csrf(csrf -> csrf.disable());
}
```

- **CSRF deshabilitado** (API REST stateless con JWT)
- **BCryptPasswordEncoder** para contraseñas
- **Cookie de sesión:** `USUARIOS_SESSION`

---

## Comunicación entre microservicios

### → msvc-cursos (OpenFeign)

Al eliminar un usuario, se notifica a `msvc-cursos` para limpiar sus inscripciones:

```java
@FeignClient(name = "msvc-cursos")
public interface CursoClienteRest {
    @DeleteMapping("/eliminar-curso-usuario/{id}")
    void eliminarCursoUsuarioPorId(@PathVariable Long id);
}
```

### ← msvc-auth (WebClient)

`msvc-auth` llama a este servicio para obtener credenciales:

```
GET /login?email={email}
Respuesta: { id, nombre, email, password }
```

### ← msvc-cursos (OpenFeign)

`msvc-cursos` llama a este servicio para obtener datos de usuarios:

```
GET /usuarios-por-curso?ids=1,2,3
Respuesta: [ { id, nombre, email }, ... ]
```

---

## Observabilidad

### Health probes (Kubernetes)

```
/actuator/health          → Estado general
/actuator/health/liveness  → Liveness probe
/actuator/health/readiness → Readiness probe
```

### Logging a ELK

Los logs estructurados se envían a Logstash via TCP en el host `${LOGSTASH_HOST}:5000`.  
Configuración en `src/main/resources/logback-spring.xml`.  
Índice en Elasticsearch: `msvc-logs-msvc-usuarios-{fecha}`

---

## Ejecución local

```bash
# Levantar MySQL primero
docker compose up mysql8 -d

# Ejecutar el microservicio
cd msvc-usuarios
./mvnw spring-boot:run
```

Verificar:
```bash
curl http://localhost:8001/actuator/health
```

---

## Docker

### Dockerfile (multi-stage)

```
Stage 1 (builder): Amazon Corretto 17 Alpine JDK
  - Descarga dependencias Maven (.m2 cache)
  - Compila y empaqueta el JAR

Stage 2 (runtime): Amazon Corretto 17 Alpine JDK
  - Crea usuario no-root 'spring'
  - Copia JAR del stage 1
  - Expone puerto 8001
  - ENTRYPOINT: java -jar msvc-usuarios-0.0.1-SNAPSHOT.jar
```

### Construir y publicar

```bash
docker build -t miguelrodriguez15/msvc-usuarios:latest .
docker push miguelrodriguez15/msvc-usuarios:latest
```

---

## Kubernetes

### Archivo: `deployment-usuarios.yaml` + `svc-usuarios.yaml`

```yaml
Deployment:
  - Replicas: 1
  - imagePullPolicy: Always
  - Puerto: 8001
  - Profiles: kubernetes,dev,elk
  - ConfigMap: PORT, DB_HOT, DB_DATABASE, CURSOS_URL, LB_AUTH_ISSUER_URI
  - Secret: DB_USERNAME, DB_PASSWORD
  - LOGSTASH_HOST: 192.168.49.1

Health probes:
  - startupProbe:  /actuator/health (delay 10s, period 5s, failThreshold 30)
  - livenessProbe: /actuator/health/liveness (period 15s, failThreshold 3)
  - readinessProbe: /actuator/health/readiness (period 10s, failThreshold 3)

Resources:
  - Requests: 512Mi RAM, 400m CPU
  - Limits:   800Mi RAM, 500m CPU

Service:
  - Type: LoadBalancer
  - Port: 8001
  - sessionAffinity: ClientIP
```

### Comandos útiles

```bash
kubectl apply -f deployment-usuarios.yaml -f svc-usuarios.yaml
kubectl get pods -l app=msvc-usuarios
kubectl logs -l app=msvc-usuarios -f
kubectl describe pod -l app=msvc-usuarios

# Actualizar imagen
kubectl delete deployment msvc-usuarios
kubectl apply -f deployment-usuarios.yaml

# Obtener URL en Minikube
minikube service msvc-usuarios --url
```

### HPA (escalado automático)

```bash
kubectl apply -f hpa-usuarios.yaml
# Escala automáticamente de 1 a 5 réplicas cuando CPU > 50%
kubectl get hpa hpa-msvc-usuarios
```
