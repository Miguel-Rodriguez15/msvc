# Proyecto Microservicios - Spring Boot + Kubernetes

Arquitectura de microservicios con Spring Boot 3.5, Spring Cloud 2025, OAuth2 y Kubernetes. El sistema gestiona usuarios y cursos con autenticación centralizada, descubrimiento de servicios en K8s, escalado automático y observabilidad con el stack ELK.

---

## Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Microservicios](#microservicios)
- [Tecnologías](#tecnologías)
- [Requisitos Previos](#requisitos-previos)
- [Ejecución Local con Docker Compose](#ejecución-local-con-docker-compose)
- [Despliegue en Kubernetes](#despliegue-en-kubernetes)
- [Stack ELK - Observabilidad](#stack-elk---observabilidad)
- [Seguridad y OAuth2](#seguridad-y-oauth2)
- [API Reference](#api-reference)
- [Variables de Entorno](#variables-de-entorno)
- [Estructura del Repositorio](#estructura-del-repositorio)

---

## Arquitectura

```
                        ┌─────────────────────────────────────────┐
                        │              KUBERNETES CLUSTER          │
                        │                                          │
  Cliente ──────────────►  Ingress NGINX (microservicios.local)   │
                        │      /usuarios  │  /cursos               │
                        │          │              │                │
                        │    ┌─────▼──────┐ ┌────▼────────┐       │
                        │    │msvc-gateway│ │             │       │
                        │    │  :8090     │ │             │       │
                        │    └─────┬──────┘ │             │       │
                        │          │        │             │       │
                        │    ┌─────▼──────┐ ┌────▼────────┐       │
                        │    │msvc-usuarios│ │msvc-cursos  │       │
                        │    │   :8001    │ │   :8002     │       │
                        │    └─────┬──────┘ └──────┬──────┘       │
                        │          │  Feign         │  Feign       │
                        │    ┌─────▼──────┐         │              │
                        │    │  msvc-auth │◄────────┘              │
                        │    │   :9000    │                        │
                        │    └────────────┘                        │
                        │                                          │
                        │    ┌──────────┐  ┌──────────┐           │
                        │    │  MySQL 8 │  │Postgres14│           │
                        │    │ usuarios │  │  cursos  │           │
                        │    └──────────┘  └──────────┘           │
                        └─────────────────────────────────────────┘
```

### Flujos principales

**Autenticación OAuth2:**
1. El cliente solicita autorización vía `/oauth2/authorize` en `msvc-auth` (puerto 9000)
2. `msvc-auth` consulta credenciales a `msvc-usuarios` via WebClient reactivo
3. Se emite un JWT con scopes `read`, `write`, `openid`
4. El cliente usa el token Bearer en las peticiones a `msvc-usuarios` y `msvc-cursos`

**Comunicación entre servicios:**
- `msvc-cursos` llama a `msvc-usuarios` via OpenFeign para crear/consultar usuarios
- `msvc-usuarios` llama a `msvc-cursos` via OpenFeign para limpiar relaciones al eliminar un usuario
- El descubrimiento de servicios se realiza por nombre de pod en Kubernetes (`lb://msvc-usuarios`)

---

## Microservicios

| Servicio | Puerto | Base de Datos | Descripción |
|---|---|---|---|
| `msvc-auth` | 9000 | — | Servidor de autorización OAuth2. Emite JWT. |
| `msvc-usuarios` | 8001 | MySQL 8 | CRUD de usuarios. Resource server OAuth2. |
| `msvc-cursos` | 8002 | PostgreSQL 14 | CRUD de cursos. Gestiona inscripciones. |
| `msvc-gateway` | 8090 | — | API Gateway. Enruta y balancea carga. |

Cada microservicio tiene su propio README con documentación detallada:
- [msvc-auth/README.md](msvc-auth/README.md)
- [msvc-usuarios/README.md](msvc-usuarios/README.md)
- [msvc-cursos/README.md](msvc-cursos/README.md)
- [msvc-gateway/README.md](msvc-gateway/README.md)

---

## Tecnologías

| Categoría | Tecnología |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.5.0 |
| Cloud | Spring Cloud 2025.0.0 |
| Seguridad | Spring Security 6, OAuth2, JWT |
| Persistencia | Spring Data JPA, Hibernate |
| HTTP client | OpenFeign |
| Bases de datos | MySQL 8, PostgreSQL 14 |
| Contenerización | Docker (multi-stage builds) |
| Base image | Amazon Corretto 17 Alpine |
| Orquestación | Kubernetes (Minikube / cualquier cluster) |
| Gateway | Spring Cloud Gateway (MVC) |
| Descubrimiento | Spring Cloud Kubernetes |
| Observabilidad | Spring Actuator, ELK Stack 8.11 |
| Build | Maven (multi-módulo) |

---

## Requisitos Previos

- **Docker** y **Docker Compose**
- **Java 17** (para desarrollo local sin Docker)
- **Maven 3.8+** (para compilar)
- **kubectl** (para despliegue en Kubernetes)
- **Minikube** u otro cluster Kubernetes
- **Docker Hub account** con acceso a `miguelrodriguez15/*`

---

## Ejecución Local con Docker Compose

### 1. Solo bases de datos (desarrollo)

```bash
docker compose up mysql8 postgres-cursos -d
```

### 2. Stack completo

```bash
docker compose up -d
```

Esto levanta:
- MySQL 8 en `localhost:3307`
- PostgreSQL 14 en `localhost:5532`
- msvc-usuarios en `localhost:8001`
- msvc-cursos en `localhost:8002`

> **Nota:** `msvc-auth` y `msvc-gateway` no están en el compose principal. Para el flujo OAuth2 completo, usar Kubernetes.

### 3. Verificar salud de los servicios

```bash
curl http://localhost:8001/actuator/health
curl http://localhost:8002/actuator/health
```

### 4. Detener servicios

```bash
docker compose down
# Con eliminación de volúmenes:
docker compose down -v
```

---

## Despliegue en Kubernetes

### Orden de despliegue (importante)

El orden importa por las dependencias entre servicios.

```bash
# 1. Storage (bases de datos)
kubectl apply -f mysql-pv.yaml -f mysql-pvc.yaml
kubectl apply -f postgres-pv.yaml -f postgres-pvc.yaml

# 2. Bases de datos
kubectl apply -f deployment-mysql.yaml -f svc-mysql.yaml
kubectl apply -f deployment-postgres.yaml -f svc-postgres.yaml

# 3. Configuración
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml

# 4. msvc-auth PRIMERO (servidor de autorización)
kubectl apply -f auth.yml

# 5. Microservicios de negocio
kubectl apply -f deployment-usuarios.yaml -f svc-usuarios.yaml
kubectl apply -f deployment-cursos.yaml -f svc-cursos.yaml

# 6. Gateway
kubectl apply -f gateway.yaml

# 7. Escalado automático
kubectl apply -f hpa-usuarios.yaml -f hpa-cursos.yaml

# 8. Ingress
kubectl apply -f ingress.yaml
```

### Verificar despliegue

```bash
kubectl get pods
kubectl get services
kubectl get hpa
```

### Actualizar imagen de un microservicio

```bash
# Eliminar deployment y recrear (fuerza pull de latest)
kubectl delete deployment msvc-usuarios
kubectl apply -f deployment-usuarios.yaml
```

### Obtener URLs de servicios en Minikube

```bash
minikube service msvc-auth --url
minikube service msvc-usuarios --url
minikube service msvc-cursos --url
minikube service msvc-gateway --url
```

### Configurar Ingress en Minikube

```bash
minikube addons enable ingress
# Agregar al /etc/hosts:
# <minikube-ip> microservicios.local
```

Luego acceder via:
- `http://microservicios.local/usuarios`
- `http://microservicios.local/cursos`

---

## Stack ELK - Observabilidad

Los microservicios envían logs estructurados en JSON a Logstash. Los índices en Elasticsearch siguen el patrón `msvc-logs-{service_name}-{fecha}`.

### Levantar el stack ELK

```bash
docker compose -f docker-compose-elk.yml up -d
```

| Servicio | URL |
|---|---|
| Elasticsearch | http://localhost:9200 |
| Kibana | http://localhost:5601 |
| Logstash TCP input | localhost:5000 |

### Verificar logs en Kibana

1. Abrir http://localhost:5601
2. Crear index pattern: `msvc-logs-*`
3. Explorar logs por campo `service_name`

### Configuración de Logstash

El pipeline está en `logstash/pipeline/logstash.conf`. Recibe JSON por TCP en el puerto 5000 y crea índices dinámicos por microservicio y fecha.

---

## Seguridad y OAuth2

### Grant Type

El proyecto usa **Authorization Code** con PKCE implícito.

```
Client ID:     usuarios-client
Client Secret: 12345
Scopes:        openid, read, write
```

### Obtener token (flujo manual)

```bash
# 1. Iniciar flujo de autorización
GET http://<auth-server>:9000/oauth2/authorize?
    response_type=code&
    client_id=usuarios-client&
    scope=openid read write&
    redirect_uri=<redirect_uri>

# 2. Intercambiar código por token
POST http://<auth-server>:9000/oauth2/token
  grant_type=authorization_code&
  code=<code>&
  redirect_uri=<redirect_uri>
  Authorization: Basic dXN1YXJpb3MtY2xpZW50OjEyMzQ1

# 3. Usar token
GET http://localhost:8001/
  Authorization: Bearer <access_token>
```

### Scopes y permisos

| Scope | Endpoints permitidos |
|---|---|
| `read` | GET en todos los recursos |
| `write` | POST, PUT, DELETE en todos los recursos |

---

## API Reference

### msvc-usuarios (`localhost:8001`)

| Método | Ruta | Descripción | Scope requerido |
|---|---|---|---|
| GET | `/` | Listar usuarios | `read` |
| GET | `/{id}` | Detalle usuario | `read` |
| POST | `/` | Crear usuario | `write` |
| PUT | `/{id}` | Editar usuario | `write` |
| DELETE | `/{id}` | Eliminar usuario | `write` |
| GET | `/usuarios-por-curso?ids=1,2,3` | Usuarios por IDs | `read` |
| GET | `/login?email=x@x.com` | Login por email (interno) | público |

### msvc-cursos (`localhost:8002`)

| Método | Ruta | Descripción | Scope requerido |
|---|---|---|---|
| GET | `/` | Listar cursos | — |
| GET | `/{id}` | Detalle curso con usuarios | Bearer token |
| POST | `/` | Crear curso | — |
| PUT | `/{id}` | Editar curso | — |
| DELETE | `/{id}` | Eliminar curso | — |
| PUT | `/asignar-usuario/{cursoId}` | Asignar usuario a curso | — |
| POST | `/crear-usuario/{cursoId}` | Crear usuario y asignarlo | — |
| DELETE | `/eliminar-usuario/{cursoId}` | Desasignar usuario | — |

### msvc-gateway (`localhost:8090`)

| Prefijo | Destino |
|---|---|
| `/api/usuarios/**` | `msvc-usuarios` |
| `/api/cursos/**` | `msvc-cursos` |

---

## Variables de Entorno

### msvc-auth

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `LB_AUTH_REDIRECT_URI` | URI de redirección OAuth2 | — |
| `LB_USUARIOS_URI` | URL de msvc-usuarios | — |

### msvc-usuarios

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto del servidor | `8001` |
| `DB_HOT` | Host:puerto de MySQL | `mysql8:3306` |
| `DB_DATABASE` | Nombre de la base de datos | `msvc_usuarios` |
| `DB_USERNAME` | Usuario de BD | `root` |
| `DB_PASSWORD` | Contraseña de BD | `admin123` |
| `CURSOS_URL` | URL de msvc-cursos | `msvc-cursos:8002` |
| `LB_AUTH_ISSUER_URI` | Issuer URI del servidor OAuth2 | `http://127.0.0.1:9000` |

### msvc-cursos

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto del servidor | `8002` |
| `DB_HOST` | Host:puerto de PostgreSQL | `postgres14:5432` |
| `DB_DATABASE` | Nombre de la base de datos | `msvc_cursos` |
| `DB_USERNAME` | Usuario de BD | `postgres` |
| `DB_PASSWORD` | Contraseña de BD | `admin123` |
| `USUARIOS_URL` | URL de msvc-usuarios | `msvc-usuarios:8001` |

---

## Estructura del Repositorio

```
proyecto-microservicios/
├── pom.xml                         # POM padre multi-módulo
├── docker-compose.yml              # Stack local (MySQL, PostgreSQL, servicios)
├── docker-compose-elk.yml          # Stack ELK (Elasticsearch, Logstash, Kibana)
├── logstash/
│   └── pipeline/logstash.conf      # Pipeline de ingesta de logs
│
├── msvc-auth/                      # Servidor de autorización OAuth2
├── msvc-usuarios/                  # Microservicio de usuarios
├── msvc-cursos/                    # Microservicio de cursos
├── msvc-gateway/                   # API Gateway
│
├── Kubernetes - Bases de datos:
│   ├── deployment-mysql.yaml / svc-mysql.yaml
│   ├── deployment-postgres.yaml / svc-postgres.yaml
│   ├── mysql-pv.yaml / mysql-pvc.yaml
│   └── postgres-pv.yaml / postgres-pvc.yaml
│
├── Kubernetes - Microservicios:
│   ├── auth.yml                    # Deployment + Service de msvc-auth
│   ├── deployment-usuarios.yaml / svc-usuarios.yaml
│   ├── deployment-cursos.yaml / svc-cursos.yaml
│   └── gateway.yaml               # Deployment + Service de msvc-gateway
│
├── Kubernetes - Configuración:
│   ├── configmap.yaml             # Variables de entorno por microservicio
│   └── secret.yaml               # Credenciales de bases de datos
│
└── Kubernetes - Infraestructura:
    ├── hpa-usuarios.yaml          # HPA: escala hasta 5 réplicas al 50% CPU
    ├── hpa-cursos.yaml
    └── ingress.yaml               # Ingress NGINX con rate limiting
```

---

## Imágenes Docker Hub

```
miguelrodriguez15/msvc-auth:latest
miguelrodriguez15/msvc-usuarios:latest
miguelrodriguez15/msvc-cursos:latest
miguelrodriguez15/msvc-gateway:latest
```

### Construir y publicar una imagen

```bash
cd msvc-usuarios
docker build -t miguelrodriguez15/msvc-usuarios:latest .
docker push miguelrodriguez15/msvc-usuarios:latest
```
