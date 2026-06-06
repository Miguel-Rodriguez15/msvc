# msvc-gateway — API Gateway

Gateway de entrada para el sistema de microservicios. Enruta las peticiones externas hacia `msvc-usuarios` y `msvc-cursos` usando Spring Cloud Gateway con Load Balancer de Kubernetes. Actúa como punto único de acceso a la API.

---

## Tabla de Contenidos

- [Responsabilidad](#responsabilidad)
- [Tecnologías](#tecnologías)
- [Configuración](#configuración)
- [Rutas](#rutas)
- [Ejecución local](#ejecución-local)
- [Docker](#docker)
- [Kubernetes](#kubernetes)

---

## Responsabilidad

- Enrutar peticiones externas al microservicio correcto
- Balancear carga entre réplicas usando el descubrimiento de Kubernetes
- Quitar el prefijo de ruta antes de reenviar la petición

No implementa autenticación ni autorización. El control de acceso es responsabilidad de cada microservicio (`msvc-usuarios` como Resource Server).

---

## Tecnologías

| Dependencia | Versión | Propósito |
|---|---|---|
| Spring Boot | 3.5.0 | Framework base |
| Spring Cloud Gateway (MVC) | 2025.0.0 | Enrutamiento HTTP |
| Spring Cloud Kubernetes All | 2025.0.0 | Descubrimiento + Load Balancer |

---

## Configuración

### `application.properties`

```properties
spring.application.name=msvc-gateway
server.port=8090
```

### `application.yaml` (rutas)

```yaml
spring:
  cloud:
    gateway:
      mvc:
        routes:
          - id: msvc-usuarios
            uri: lb://msvc-usuarios
            predicates:
              - Path=/api/usuarios/**
            filters:
              - StripPrefix=2

          - id: msvc-cursos
            uri: lb://msvc-cursos
            predicates:
              - Path=/api/cursos/**
            filters:
              - StripPrefix=2
```

---

## Rutas

| Entrada (Gateway) | Destino | Ruta resultante |
|---|---|---|
| `GET /api/usuarios/` | `lb://msvc-usuarios` | `GET /` |
| `GET /api/usuarios/{id}` | `lb://msvc-usuarios` | `GET /{id}` |
| `POST /api/usuarios/` | `lb://msvc-usuarios` | `POST /` |
| `GET /api/cursos/` | `lb://msvc-cursos` | `GET /` |
| `GET /api/cursos/{id}` | `lb://msvc-cursos` | `GET /{id}` |
| `POST /api/cursos/` | `lb://msvc-cursos` | `POST /` |

El filtro `StripPrefix=2` elimina los dos primeros segmentos del path (`/api/usuarios`) antes de reenviar la petición, por lo que el microservicio recibe la ruta sin ese prefijo.

### Esquema de enrutamiento

```
Cliente
  │
  ▼
msvc-gateway :8090
  │
  ├── /api/usuarios/** ──► lb://msvc-usuarios :8001
  │                              (K8s Service Discovery)
  │
  └── /api/cursos/**  ──► lb://msvc-cursos :8002
                                 (K8s Service Discovery)
```

El prefijo `lb://` indica que se usa el Load Balancer de Spring Cloud Kubernetes para resolver el nombre del servicio.

---

## Ejecución local

Para ejecutar localmente, los microservicios destino deben estar disponibles. El descubrimiento de Kubernetes no está disponible fuera del cluster, por lo que es necesario ajustar las URIs en `application.yaml`:

```yaml
# Para desarrollo local (sin K8s):
uri: http://localhost:8001   # en lugar de lb://msvc-usuarios
uri: http://localhost:8002   # en lugar de lb://msvc-cursos
```

```bash
cd msvc-gateway
./mvnw spring-boot:run
```

---

## Docker

### Dockerfile (multi-stage)

```
Stage 1 (builder): Amazon Corretto 17 Alpine JDK
  - Descarga dependencias Maven (.m2 cache)
  - Compila y empaqueta el JAR

Stage 2 (runtime): Amazon Corretto 17 Alpine JDK
  - Copia JAR del stage 1
  - Expone puerto 8090
  - CMD: java -jar msvc-gateway-0.0.1-SNAPSHOT.jar
```

### Construir y publicar

```bash
docker build -t miguelrodriguez15/msvc-gateway:latest .
docker push miguelrodriguez15/msvc-gateway:latest
```

---

## Kubernetes

### Archivo: `gateway.yaml`

Contiene el Deployment y el Service en un solo archivo.

```yaml
Deployment:
  - Replicas: 1
  - Image: miguelrodriguez15/msvc-gateway:latest
  - Puerto: 8090

Service:
  - Type: LoadBalancer
  - Puerto externo: 8090 → targetPort: 8090
```

### Desplegar

```bash
kubectl apply -f gateway.yaml
kubectl get pods -l app=msvc-gateway
kubectl logs -l app=msvc-gateway -f
```

### Obtener URL en Minikube

```bash
minikube service msvc-gateway --url
# Ejemplo: http://192.168.49.2:32XXX
```

### Probar el gateway

```bash
GATEWAY_URL=$(minikube service msvc-gateway --url)

# Listar usuarios a través del gateway
curl $GATEWAY_URL/api/usuarios/ \
  -H "Authorization: Bearer <token>"

# Listar cursos a través del gateway
curl $GATEWAY_URL/api/cursos/
```

### Ingress como alternativa al gateway directo

El proyecto también incluye `ingress.yaml` que expone los microservicios directamente (sin pasar por el gateway) usando NGINX con rate limiting:

```
http://microservicios.local/usuarios → msvc-usuarios:8001
http://microservicios.local/cursos   → msvc-cursos:8002

Rate limiting:
  - 10 req/s por IP
  - 5 conexiones simultáneas
  - Burst: x3
  - Status 429 al superar el límite
```
