# msvc-auth — Servidor de Autorización OAuth2

Microservicio encargado de la autenticación y emisión de tokens JWT. Implementa el protocolo **OAuth2 Authorization Code** con Spring Authorization Server. Es el primer servicio que debe levantarse en el cluster.

---

## Tabla de Contenidos

- [Responsabilidad](#responsabilidad)
- [Tecnologías](#tecnologías)
- [Configuración](#configuración)
- [Seguridad y OAuth2](#seguridad-y-oauth2)
- [Comunicación con msvc-usuarios](#comunicación-con-msvc-usuarios)
- [Endpoints](#endpoints)
- [Ejecución local](#ejecución-local)
- [Docker](#docker)
- [Kubernetes](#kubernetes)

---

## Responsabilidad

`msvc-auth` actúa como **Authorization Server** en el esquema OAuth2. Sus responsabilidades son:

1. Autenticar usuarios consultando sus credenciales a `msvc-usuarios` vía WebClient
2. Emitir tokens JWT firmados con clave RSA 2048 bits
3. Gestionar el flujo Authorization Code con consentimiento del usuario
4. Exponer los endpoints estándar de OAuth2 (`/oauth2/authorize`, `/oauth2/token`, etc.)

No tiene base de datos propia. Los usuarios los obtiene en tiempo real desde `msvc-usuarios`.

---

## Tecnologías

| Dependencia | Versión | Propósito |
|---|---|---|
| Spring Boot | 3.5.0 | Framework base |
| Spring Security | 6.x | Autenticación |
| Spring Authorization Server | integrado | Servidor OAuth2 |
| Spring WebFlux | integrado | WebClient reactivo |
| Spring Cloud Kubernetes | 2025.0.0 | Descubrimiento de servicios |

---

## Configuración

### `application.properties`

```properties
spring.application.name=msvc-auth
server.port=9000
```

### Variables de entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `LB_AUTH_REDIRECT_URI` | URI de redirección OAuth2 del cliente | `http://192.168.49.2:31415` |
| `LB_USUARIOS_URI` | URL base de msvc-usuarios | `http://192.168.49.2:31415` |

---

## Seguridad y OAuth2

### Cliente registrado

```
Client ID:     usuarios-client
Client Secret: 12345
Grant Types:   authorization_code, refresh_token
Scopes:        openid, read, write
Require PKCE:  false
Consent:       requerido
```

### Generación de claves JWT

Las claves RSA se generan **en memoria** al arrancar la aplicación. Esto significa que si el pod se reinicia, los tokens emitidos previamente dejarán de ser válidos.

```java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
KeyPair keyPair = generator.generateKeyPair();
```

### Endpoints OAuth2 estándar

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/oauth2/authorize` | Iniciar flujo de autorización |
| POST | `/oauth2/token` | Intercambiar código por token |
| GET | `/oauth2/jwks` | Claves públicas para validar JWT |
| POST | `/oauth2/revoke` | Revocar token |
| GET | `/.well-known/openid-configuration` | Metadatos del servidor |

### Flujo de autorización

```
1. Cliente → GET /oauth2/authorize?response_type=code&client_id=usuarios-client&scope=openid read write&redirect_uri=<uri>
2. Usuario ve pantalla de login → ingresa email y password
3. msvc-auth consulta msvc-usuarios: GET http://msvc-usuarios/login?email=<email>
4. Si credenciales válidas → pantalla de consentimiento de scopes
5. Usuario aprueba → redirect a redirect_uri?code=<code>
6. Cliente → POST /oauth2/token con code y credentials
7. Respuesta: { access_token, refresh_token, id_token, expires_in }
```

---

## Comunicación con msvc-usuarios

`msvc-auth` no tiene acceso directo a la base de datos. Para cargar los `UserDetails` durante la autenticación, llama a `msvc-usuarios`:

```
GET http://msvc-usuarios/login?email={email}
```

La respuesta es un objeto `Usuario` con `{ id, nombre, email, password }` donde `password` ya está hasheada con BCrypt. Spring Security hace la validación del hash internamente.

La llamada usa `WebClient` con Load Balancer de Kubernetes (`lb://msvc-usuarios`).

### Archivo: `UsuarioService.java`

```java
implements UserDetailsService

@Override
public UserDetails loadUserByUsername(String email) {
    // Llama a msvc-usuarios via WebClient
    // Construye UserDetails con rol ROLE_USER
}
```

---

## Endpoints

Este servicio no expone endpoints de negocio propios. Solo los endpoints estándar de Spring Authorization Server listados arriba.

---

## Ejecución local

Para ejecutar localmente, `msvc-usuarios` debe estar disponible en `http://127.0.0.1:9000` (o ajustar `LB_USUARIOS_URI`).

```bash
cd msvc-auth
./mvnw spring-boot:run
```

---

## Docker

### Dockerfile (multi-stage)

```
Stage 1 (builder): Amazon Corretto 17 Alpine JDK
  - Descarga dependencias Maven
  - Compila el proyecto

Stage 2 (runtime): Amazon Corretto 17 Alpine JDK
  - Copia JAR del stage 1
  - Expone puerto 9000
  - CMD: java -jar msvc-auth-0.0.1-SNAPSHOT.jar
```

### Construir imagen

```bash
docker build -t miguelrodriguez15/msvc-auth:latest .
docker push miguelrodriguez15/msvc-auth:latest
```

---

## Kubernetes

### Archivo: `auth.yml`

Contiene el Deployment y el Service en un solo archivo.

```yaml
Deployment:
  - Replicas: 1
  - Image: miguelrodriguez15/msvc-auth:latest
  - Puerto: 9000
  - Variables de entorno: MY_POD_NAME, MY_POD_IP,
                          LB_AUTH_REDIRECT_URI, LB_USUARIOS_URI
                          (desde ConfigMap msvc-usuarios)

Service:
  - Type: LoadBalancer
  - Puerto externo: 9000
```

### Desplegar

```bash
# msvc-auth debe levantarse ANTES que msvc-usuarios
kubectl apply -f auth.yml
kubectl get pods -l app=msvc-auth
kubectl logs -l app=msvc-auth -f
```

### Obtener URL en Minikube

```bash
minikube service msvc-auth --url
```
