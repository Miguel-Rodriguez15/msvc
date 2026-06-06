# msvc-cursos — Microservicio de Cursos

Microservicio encargado de la gestión de cursos y la inscripción de usuarios en ellos. Usa PostgreSQL 14 como base de datos y OpenFeign para comunicarse con `msvc-usuarios`. No tiene seguridad OAuth2 propia, pero propaga el token Bearer recibido hacia `msvc-usuarios` en las peticiones que lo requieren.

---

## Tabla de Contenidos

- [Responsabilidad](#responsabilidad)
- [Tecnologías](#tecnologías)
- [Configuración](#configuración)
- [Modelo de datos](#modelo-de-datos)
- [API Endpoints](#api-endpoints)
- [Lógica de negocio](#lógica-de-negocio)
- [Comunicación entre microservicios](#comunicación-entre-microservicios)
- [Observabilidad](#observabilidad)
- [Ejecución local](#ejecución-local)
- [Docker](#docker)
- [Kubernetes](#kubernetes)

---

## Responsabilidad

- CRUD completo de cursos
- Gestionar inscripciones: asignar, crear y desasignar usuarios de un curso
- Obtener el detalle de un curso con su lista de usuarios (agrega datos de `msvc-usuarios`)
- Limpiar inscripciones cuando un usuario es eliminado desde `msvc-usuarios`

---

## Tecnologías

| Dependencia | Versión | Propósito |
|---|---|---|
| Spring Boot | 3.5.0 | Framework base |
| Spring Data JPA | integrado | ORM con Hibernate |
| Spring Cloud OpenFeign | 2025.0.0 | Cliente HTTP declarativo |
| Spring Cloud Kubernetes | 2025.0.0 | Descubrimiento de servicios |
| Spring Actuator | integrado | Health probes y métricas |
| PostgreSQL Driver | integrado | Driver JDBC |
| Jakarta Validation | integrado | Validaciones de entidad |
| Logstash Logback Encoder | 7.4 | Logging estructurado a ELK |

---

## Configuración

### `application.properties`

```properties
spring.application.name=msvc-cursos
server.port=${PORT:8002}

# Base de datos PostgreSQL
spring.datasource.url=jdbc:postgresql://${DB_HOST:postgres-cursos:5432}/${DB_DATABASE:msvc_cursos}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres123}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.generate-ddl=true

# Actuator (health probes para K8s)
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

### Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto del servidor | `8002` |
| `DB_HOST` | Host:puerto de PostgreSQL | `postgres14:5432` |
| `DB_DATABASE` | Nombre de base de datos | `msvc_cursos` |
| `DB_USERNAME` | Usuario PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña PostgreSQL | `admin123` |
| `USUARIOS_URL` | URL de msvc-usuarios (Feign) | `msvc-usuarios:8001` |
| `SPRING_PROFILES_ACTIVE` | Perfiles activos | `kubernetes,elk` |
| `LOGSTASH_HOST` | IP del servidor Logstash | `localhost` |

---

## Modelo de datos

### Entidad `Curso`

```java
@Entity
@Table(name = "cursos")
public class Curso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty
    private String nombre;

    @OneToMany(mappedBy = "curso", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CursoUsuario> cursoUsuarios = new ArrayList<>();

    @Transient  // No se persiste, se llena desde msvc-usuarios
    private List<Usuario> usuarios = new ArrayList<>();
}
```

### Entidad `CursoUsuario` (tabla de unión)

```java
@Entity
@Table(name = "cursos_usuarios")
public class CursoUsuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long usuarioId;  // ID del usuario en msvc-usuarios
}
```

### DTO `Usuario` (no es entidad)

```java
public class Usuario {
    private Long id;
    private String nombre;
    private String email;
    private String password;
}
```

**Base de datos:** `msvc_cursos` en PostgreSQL 14  
**Tablas:** `cursos`, `cursos_usuarios`  
**Schema:** generado automáticamente por Hibernate

---

## API Endpoints

### Cursos

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/` | Listar todos los cursos |
| GET | `/{id}` | Detalle del curso (con lista de usuarios) |
| POST | `/` | Crear nuevo curso |
| PUT | `/{id}` | Editar curso |
| DELETE | `/{id}` | Eliminar curso |

### Inscripciones

| Método | Ruta | Descripción |
|---|---|---|
| PUT | `/asignar-usuario/{cursoId}` | Asignar usuario existente al curso |
| POST | `/crear-usuario/{cursoId}` | Crear usuario en msvc-usuarios y asignarlo |
| DELETE | `/eliminar-usuario/{cursoId}` | Desasignar usuario del curso |
| DELETE | `/eliminar-curso-usuario/{id}` | Callback desde msvc-usuarios (por usuarioId) |

### Ejemplo `GET /{id}`

Requiere header `Authorization: Bearer <token>`:

```json
{
  "id": 1,
  "nombre": "Spring Boot Avanzado",
  "cursoUsuarios": [
    { "id": 1, "usuarioId": 5 }
  ],
  "usuarios": [
    { "id": 5, "nombre": "Juan Pérez", "email": "juan@email.com" }
  ]
}
```

La lista `usuarios` se obtiene llamando a `msvc-usuarios` con el token recibido.

### Ejemplo `PUT /asignar-usuario/{cursoId}`

```json
{ "id": 5 }
```

Verifica que el usuario exista en `msvc-usuarios` antes de crear la relación.

### Ejemplo `POST /crear-usuario/{cursoId}`

```json
{
  "nombre": "María López",
  "email": "maria@email.com",
  "password": "clave123"
}
```

Crea el usuario en `msvc-usuarios` y automáticamente lo asigna al curso.

---

## Lógica de negocio

### Obtener curso con usuarios (`porIdConUsuarios`)

1. Busca el curso en la BD local (incluye los `CursoUsuario`)
2. Extrae la lista de IDs de usuarios: `cursoUsuarios.stream().map(CursoUsuario::getUsuarioId)`
3. Llama a `msvc-usuarios` via Feign: `GET /usuarios-por-curso?ids=1,2,3` con el token Bearer
4. Popula el campo `@Transient usuarios` con la respuesta

### Eliminar usuario en cascada

Cuando `msvc-usuarios` elimina un usuario, llama a:
```
DELETE /eliminar-curso-usuario/{usuarioId}
```

`msvc-cursos` ejecuta:
```sql
DELETE FROM cursos_usuarios WHERE usuario_id = ?
```

Esto mantiene la consistencia sin necesidad de claves foráneas entre bases de datos.

---

## Comunicación entre microservicios

### → msvc-usuarios (OpenFeign)

```java
@FeignClient(name = "msvc-usuarios")
public interface UsuarioClientRest {

    @GetMapping("/{id}")
    Usuario detalle(@PathVariable Long id);

    @PostMapping("/")
    Usuario crear(@RequestBody Usuario usuario);

    @GetMapping("/usuarios-por-curso")
    List<Usuario> obtenerAlumnosPorCurso(
        @RequestParam Iterable<Long> ids,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String token
    );
}
```

Las llamadas de Feign usan el nombre del servicio (`msvc-usuarios`) que se resuelve por descubrimiento en Kubernetes.

### ← msvc-usuarios (callback)

`msvc-usuarios` llama a este servicio al eliminar un usuario:

```
DELETE /eliminar-curso-usuario/{id}
```

---

## Observabilidad

### Health probes (Kubernetes)

```
/actuator/health           → Estado general
/actuator/health/liveness  → Liveness probe
/actuator/health/readiness → Readiness probe
```

### Logging a ELK

Los logs estructurados se envían a Logstash via TCP en `${LOGSTASH_HOST}:5000`.  
Configuración en `src/main/resources/logback-spring.xml`.  
Índice en Elasticsearch: `msvc-logs-msvc-cursos-{fecha}`

---

## Ejecución local

```bash
# Levantar PostgreSQL primero
docker compose up postgres-cursos -d

# Ejecutar el microservicio
cd msvc-cursos
./mvnw spring-boot:run
```

Verificar:
```bash
curl http://localhost:8002/actuator/health
curl http://localhost:8002/
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
  - Expone puerto 8002
  - ENTRYPOINT: java -jar msvc-cursos-0.0.1-SNAPSHOT.jar
```

### Construir y publicar

```bash
docker build -t miguelrodriguez15/msvc-cursos:latest .
docker push miguelrodriguez15/msvc-cursos:latest
```

---

## Kubernetes

### Archivo: `deployment-cursos.yaml` + `svc-cursos.yaml`

```yaml
Deployment:
  - Replicas: 1
  - imagePullPolicy: Always
  - Puerto: 8002
  - Profiles: kubernetes,elk
  - ConfigMap: PORT, DB_HOST, DB_DATABASE, USUARIOS_URL
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
  - Port: 8002
```

### Comandos útiles

```bash
kubectl apply -f deployment-cursos.yaml -f svc-cursos.yaml
kubectl get pods -l app=msvc-cursos
kubectl logs -l app=msvc-cursos -f
kubectl describe pod -l app=msvc-cursos

# Actualizar imagen
kubectl delete deployment msvc-cursos
kubectl apply -f deployment-cursos.yaml

# Obtener URL en Minikube
minikube service msvc-cursos --url
```

### HPA (escalado automático)

```bash
kubectl apply -f hpa-cursos.yaml
# Escala automáticamente de 1 a 5 réplicas cuando CPU > 50%
kubectl get hpa hpa-msvc-cursos
```
