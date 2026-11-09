<h1 align="center">🛡️ Campus Seguro</h1>
<h3 align="center">ms06-events</h3>

<p align="center"><em>Microservicio de Eventos: crea, programa, ejecuta, finaliza y evalúa eventos y simulacros de emergencia del campus, y coordina a los demás módulos cuando un evento cambia de estado.</em></p>

<p align="center">
  <img alt="Curso" src="https://img.shields.io/badge/Curso-DOSW%202026--2-1f6feb">
  <img alt="Squad" src="https://img.shields.io/badge/Squad-Zephyros-8957e5">
  <img alt="Cobertura" src="https://img.shields.io/badge/94%20%25-brightgreen">
</p>

<p align="center">
  <a href=https://github.com/CampusSeguro-DOSW-2026-2/ms06-drills.git>📦 Repositorio</a> ·
  <a href=https://dosw-team.atlassian.net/jira/software/projects/CS/boards/35/backlog?epics=visible&atlOrigin=eyJpIjoiZDc0MDU0NjQzODUwNDczZWFhOTU4ZjUyYjRlYzFkMzYiLCJwIjoiaiJ9>📋 Jira</a> ·
  <a href="TU_URL">📝 Requerimientos</a> ·
  <a href="TU_URL">📖 Swagger</a> ·
  <a href="TU_URL">🏗️ Diagramas C4</a>
</p>

---
## 📚 Ficha del documento

| | |
|:--|:--|
| **Curso** | Desarrollo y Operaciones de Software 2026-2 |
| **Fecha** | 06/11/2026 |
| **Versión** | 3.0 |

## 👥 Equipo

| Integrante | Rol | GitHub |
|:--|:--|:--|
| Rodrigo Humberto Gualtero Martínez | Product Owner | [ginnko2019](https://github.com/ginnko2019) |
| Tomás Olaya Díaz | Scrum Master | [iAxstral](https://github.com/iAxstral) |
| José Daniel García Pineda | Equipo de desarrollo | [KenjiMaster](https://github.com/KenjiMaster) |
| Jerónimo Moreno Herrera | Equipo de desarrollo | [Dracodec113](https://github.com/Dracodec113) |
| Paula Alejandra Novoa Castellanos | Equipo de desarrollo | [Aleja15-31](https://github.com/Aleja15-31) |
| Derly Valeria Pachón Pinzón | Equipo de desarrollo | [itsValepP](https://github.com/itsValepP) |

---

<a id="indice"></a>

## 🧭 Índice

| Bloque | Secciones |
|:--|:--|
| **🧠 Conocer el servicio** | [1. Explicación](#explicacion) · [2. Análisis](#analisis)<br>↳ [2.1 Contexto y objetivos](#21-contexto-y-objetivos) · [2.2 Alcance](#22-alcance) · [2.3 Reglas de negocio](#23-reglas-de-negocio) |
| **🏗️ Diseño** | [3. Arquitectura](#arquitectura) · [4. Diagramas](#diagramas) · [5. Modelo de datos](#modelo-de-datos)<br>↳ [4.1 Componentes](#41-diagrama-de-componentes) · [4.2 Clases](#42-diagrama-de-clases) · [4.3 Entidad-relación](#43-diagrama-entidad-relación) · [4.4 Infraestructura](#44-diagrama-de-infraestructura) · [4.5 Otros](#45-otros-opcional) |
| **🔌 Uso e integración** | [6. API y Swagger](#api-swagger) · [7. Seguridad](#seguridad) |
| **⚙️ Operación** | [8. Configuración](#configuracion) · [9. Cómo ejecutarlo](#ejecucion) · [10. Pruebas y cobertura](#pruebas) · [11. Infraestructura y CI/CD](#infraestructura) |
| **📁 Anexo** | [12. Estructura del proyecto](#estructura) |

---

## 📖 1. Explicación <a id="explicacion"></a>

**¿Qué es?** MS06 es el microservicio que administra el ciclo de vida de los eventos de emergencia y los simulacros: creación, edición, programación, inicio automático, monitoreo, finalización y evaluación. Cuando un evento cambia de estado avisa a los demás módulos (alertas, check-in, brigadistas) y deja registro de auditoría.

**¿Qué problema resuelve?** Sin un dueño único del evento, cada módulo (alertas, check-in, brigadistas, analítica) tendría su propia versión de cuándo empieza y termina una emergencia o un simulacro. MS06 es la fuente de verdad del estado del evento y sincroniza a los otros módulos, tolerando que alguno esté caído.

**¿Quién lo usa?**

| Actor | Qué hace con el servicio |
|-------|--------------------------|
| Coordinador | Crea, edita, programa y finaliza eventos reales (emergencias); crea tipos de evento. |
| Organizador | Crea, edita, programa y finaliza simulacros. |
| Administrador / Auditor | Consulta eventos, detalle y monitoreo (solo lectura). |
| Comunidad (estudiantes, profesores, administrativos, brigadistas) | Consulta sus eventos activos y el panel del evento en curso. |
| Otros servicios (MS01, MS02, MS03, MS04, MS05, MS08) | MS06 los llama para validar datos, crear alertas, habilitar check-in, sincronizar brigadistas y auditar. |

**¿Qué valor aporta al proyecto?** Permite que una emergencia o un simulacro se prepare, arranque a su hora, se monitoree en tiempo real y se evalúe al terminar, con todos los módulos coordinados y con trazabilidad completa de quién hizo qué.

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 🔍 2. Análisis <a id="analisis"></a>

### 2.1 Contexto y objetivos
- **Contexto:** MS06 es el módulo de eventos de Campus Seguro. Recibe peticiones a través del API Gateway, valida con MS01 (identidad) y MS02 (campus), y coordina a MS03 (alertas), MS04 (check-in), MS05 (brigadistas e incidentes) y MS08 (analítica y auditoría).
- **Objetivo general:** gestionar el ciclo de vida completo de los eventos y simulacros del campus de forma confiable aun cuando otros módulos fallen.
- **Objetivos específicos:**
  - Crear, editar y programar eventos (RF-EVE-01, RF-EVE-02).
  - Consultar, buscar y monitorear eventos (RF-EVE-03).
  - Finalizar eventos y evaluarlos automáticamente (RF-EVE-04).
  - Iniciar eventos a su hora y mostrar el panel del evento en curso (RF-EVE-05).
  - Auditar cada acción relevante hacia MS08 sin bloquear la operación.

### 2.2 Alcance

| Dentro del alcance | Fuera del alcance |
|--------------------|-------------------|
| Crear, editar, programar, iniciar y finalizar eventos y simulacros | Autenticación y usuarios (MS01) |
| Catálogo de tipos de evento | Rutas de evacuación, edificios y zonas (MS02) |
| Consulta, búsqueda, detalle y monitoreo en tiempo real | Envío y entrega de las alertas (MS03) |
| Historial de estados y evaluación automática del evento | Registro de los check-in de las personas (MS04) |
| Copia local de los brigadistas del evento | Registro y atención de incidentes (MS05) |
| Bandeja de auditoría y envío a MS08 | Consulta del historial de auditoría e indicadores analíticos (MS08) |

### 2.3 Reglas de negocio

| Código | Regla |
|--------|-------|
| RN-01 | Los estados son BORRADOR (DRAFT), PROGRAMADO (SCHEDULED), EN EJECUCIÓN (IN_PROGRESS) y FINALIZADO (FINISHED). Transiciones: DRAFT ↔ SCHEDULED → IN_PROGRESS → FINISHED. FINISHED es definitivo. |
| RN-02 | Un evento tiene nombre (máx. 150 caracteres), descripción opcional (máx. 1000) y de 1 a 4 tipos de evento activos. |
| RN-03 | La fecha de inicio debe ser futura al crear o programar. En un evento en ejecución no se puede cambiar. |
| RN-04 | Un evento es simulacro si uno de sus tipos es SIMULACRO. El coordinador gestiona eventos reales y el organizador los simulacros. ADMIN y AUDITOR solo consultan. Un simulacro no puede pasar a ser evento real ni al revés. |
| RN-05 | Solo los roles de la comunidad pueden ser convocados a un evento. |
| RN-06 | Un evento se programa solo si tiene fecha, alcance (edificios o zonas) y participantes; si falta algo o un módulo no responde, queda como borrador y se avisa con una advertencia. |
| RN-07 | Pueden existir varios eventos en curso a la vez. |
| RN-08 | El inicio es automático a la hora programada. Si CHI, BEI o ALE no responden, el evento inicia igual: la falla se guarda y se reintenta hasta lograrlo. |
| RN-09 | Al finalizar: si CHI o BEI no responden, el evento no finaliza (503); si ALE no responde, finaliza con advertencia. |
| RN-10 | Un evento finalizado no se puede editar. Un evento en ejecución no puede quedar sin alcance ni participantes. |
| RN-11 | Al finalizar se guarda la evaluación (check-in, incidentes, brigadistas). Si faltan cifras de CHI o BEI se completan después automáticamente. |
| RN-12 | Cada creación, modificación, cambio de estado y finalización queda en la bandeja de auditoría dentro de la misma transacción; se envía a MS08 después. |
| RN-13 | Las ediciones simultáneas se controlan con bloqueo (409 si hay modificación concurrente). |

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 🏛️ 3. Arquitectura <a id="arquitectura"></a>

**Estilo:** orientada por microservicios.

**Descripción general:** MS06 sigue la organización por capas de MS03: controladores delgados, servicios con interfaz e implementación, repositorios JPA y mapeadores. Las llamadas a otros módulos pasan por *gateways* (interfaz + versión REST + versión *stub* para perfil `local` y pruebas). Los procesos periódicos (inicio de eventos, sincronización de brigadistas, evaluaciones pendientes y envío de auditoría) son *schedulers* delgados que solo llaman a un servicio.

| Capa / componente | Responsabilidad |
|-------------------|-----------------|
| Controller | Recibe las peticiones HTTP, valida la entrada y devuelve la respuesta. |
| Service | Lógica de negocio (interfaz en `service/`, implementación en `service/impl/`). |
| Repository | Acceso a la base de datos. |
| Entity / DTO | Modelo persistente / objetos de entrada (`dto/request`) y salida (`dto/response`). |
| Config | Seguridad, CORS, JWT, OpenAPI, reloj y planificación. |
| Exception | Errores de dominio y manejador global (`ErrorResponse`). |
| Client | Gateways hacia MS01, MS02, MS03, MS04, MS05 y MS08. |
| Scheduler | Tareas periódicas que delegan en los servicios. |
| Security | Política de acceso por rol y tipo de evento (`EventAccessPolicy`). |

**Tecnologías**

| Capa | Tecnología | Versión |
|------|-----------|---------|
| Lenguaje | Java | 21 |
| Framework | Spring Boot (Web MVC, Data JPA, Security, Validation) | 4.1.1 |
| Base de datos | PostgreSQL | 16 |
| Autenticación | Spring Security como servidor de recursos OAuth2 (JWT) | 7 |
| Contenedores | Docker (Docker Compose para la base local) | — |
| Documentación API | springdoc-openapi (Swagger UI) | 3.1.1 |
| Pruebas | JUnit 5, Mockito, Testcontainers, WireMock, JaCoCo | WireMock 3.13.1, JaCoCo 0.8.12 |

**Comunicación con otros servicios**

| Servicio | Tipo | Dirección | Para qué |
|----------|------|-----------|----------|
| API Gateway / Frontend | REST | Entrada | Peticiones de los usuarios con JWT. |
| MS01 – Usuarios y autenticación (AUT) | REST | Salida | Validar los roles que se convocan al evento. |
| MS02 – Campus (CAM) | REST | Salida | Validar edificios y zonas del alcance y obtener las rutas del panel. |
| MS03 – Alertas (ALE) | REST | Salida | Crear, reprogramar, volver a borrador y activar las alertas del evento. |
| MS04 – Check-in (CHI) | REST | Salida | Informar el estado del evento (habilita o deshabilita el check-in) y consultar indicadores y check-ins. |
| MS05 – Brigadistas e incidentes (BEI) | REST | Salida | Copiar los brigadistas del evento y consultar los indicadores de incidentes. |
| MS08 – Analítica y auditoría (AYA) | REST | Salida | Enviar los registros de auditoría desde la bandeja local. |

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 🗺️ 4. Diagramas <a id="diagramas"></a>

> Todos viven en `docs/diagrams/` (fuente editable + imagen).

### 4.1 Diagrama de componentes
![Componentes](docs/diagrams/componentes.png)

**Qué muestra:** el API Gateway y el frontend que llaman a MS06; dentro de MS06 los controladores, servicios, *schedulers*, repositorios y gateways; la base de datos PostgreSQL propia; y los módulos externos MS01, MS02, MS03, MS04, MS05 y MS08 con sus relaciones.

### 4.2 Diagrama de clases
![Clases](docs/diagrams/clases.png)

**Qué muestra:** los paquetes `controller`, `service` (interfaces) y `service/impl`, `repository`, `entity`, `dto/request`, `dto/response`, `mapper`, `security`, `client`, `scheduler`, `config` y `exception`, y cómo se relacionan entre sí.

### 4.3 Diagrama entidad-relación
![ER](docs/diagrams/er.png)

**Qué muestra:** las 11 tablas del servicio con sus claves primarias y foráneas: `events` como entidad central, con relación 1:N hacia alcance, participantes, brigadistas, alertas, fallas de inicio e historial de estados; 1:1 con la evaluación; N:M con los tipos de evento; y la bandeja de auditoría.

### 4.4 Diagrama de infraestructura
![Infraestructura](docs/diagrams/infraestructura.png)

**Qué muestra:** en desarrollo, MS06 corre en la máquina del desarrollador (Maven o IDE) con su PostgreSQL en un contenedor Docker, y llama por HTTP a los demás módulos o a sus *stubs*.

| Nodo | Tecnología | Puerto | Notas |
|------|-----------|--------|-------|
| Cliente | Frontend (navegador) | 5173 | Origen permitido por CORS por defecto. |
| Servicio | Spring Boot (`mvn spring-boot:run`) | 8083 | Perfil `local` por defecto, con *stubs* de los demás módulos. |
| Base de datos | PostgreSQL 16 en Docker (`ms06-events-postgres`) | 5433 | Volumen persistente `ms06-events-data`; base `campusseguro_events`. |
| MS01 · MS02 · MS03 · MS04 · MS05 · MS08 | Spring Boot | 8081 · 8082 · 8080 · 8084 · 8085 · 8088 | Puertos por defecto de las variables `MSxx_URL`. |

### 4.5 Otros (opcional)
- Contexto y contenedores (C4): `docs/diagrams/c4-context.png`, `docs/diagrams/c4-containers.png`
- Secuencia del flujo principal: `docs/diagrams/ciclo-de-vida-del-evento.png`

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 🗄️ 5. Modelo de datos <a id="modelo-de-datos"></a>

El esquema no usa Flyway: lo crea `database/schema.sql` en cada arranque (idempotente) y Hibernate solo lo valida (`ddl-auto: validate`).

| Entidad | Campos principales | Relaciones |
|---------|-------------------|------------|
| `event_types` | `id`, `code`, `name`, `active`, `created_by` | N:M con `events` (7 tipos predefinidos) |
| `events` | `id`, `name`, `description`, `status`, `simulacro`, `scheduled_start`, `started_at`, `ended_at`, `created_by`, `version` | Entidad central |
| `event_type_links` | `event_id`, `type_id` | Une `events` y `event_types` (N:M) |
| `event_scope_items` | `id`, `event_id`, `scope_type`, `scope_ref_id` | N:1 con `events` |
| `event_participants` | `id`, `event_id`, `role` | N:1 con `events` |
| `event_brigadiers` | `id`, `event_id`, `brigadier_id`, `name`, `zone_id`, `status`, `synced_at` | N:1 con `events` |
| `event_alerts` | `id`, `event_id`, `alert_id`, `purpose` | N:1 con `events` |
| `event_start_failures` | `id`, `event_id`, `module`, `last_error`, `attempts`, `resolved_at` | N:1 con `events` |
| `event_state_history` | `id`, `event_id`, `from_status`, `to_status`, `changed_by`, `reason`, `changed_at` | N:1 con `events` |
| `event_evaluations` | `id`, `event_id`, `evaluated_at`, `total_checkins`, `checkin_participation_pct`, `total_incidents`, `cases_attended`, `cases_resolved`, `avg_attention_seconds`, `details` | 1:1 con `events` |
| `pending_audit_records` | `id`, `action`, `actor`, `event_id`, `payload`, `occurred_at`, `attempts`, `last_error`, `sent_at` | Bandeja de salida hacia MS08 (`event_id` opcional) |

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 📖 6. API y documentación Swagger <a id="api-swagger"></a>

**URL base:** `http://localhost:8083/api/v1`

| Recurso | URL |
|---------|-----|
| Swagger UI | `http://localhost:8083/swagger-ui.html` |
| Especificación OpenAPI (JSON) | `http://localhost:8083/v3/api-docs` |
| Copia versionada | `docs/api/openapi.json` |

**Configuración básica**
```java
@Bean
OpenAPI openAPI() {
  return new OpenAPI()
    .info(new Info().title("ms06-events").version("1.0.0").description("MS06 – Eventos de Campus Seguro"))
    .components(new Components().addSecuritySchemes("bearerAuth",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
}
```

**Resumen de endpoints**

| Método | Ruta | Descripción | Auth | Códigos |
|--------|------|-------------|------|---------|
| POST | `/eventos` | Crear un evento (borrador o programado) | Sí: COORDINADOR, ORGANIZADOR | 201, 400, 401, 403 |
| PUT | `/eventos/{id}` | Editar un evento | Sí: COORDINADOR, ORGANIZADOR | 200, 400, 401, 403, 404, 409 |
| GET | `/eventos` | Listar y buscar por título y estado (paginado) | Sí: COORDINADOR, ORGANIZADOR, ADMIN, AUDITOR | 200, 400, 401, 403 |
| GET | `/eventos/{id}` | Detalle; en ejecución incluye monitoreo, finalizado incluye la evaluación | Sí: COORDINADOR, ORGANIZADOR, ADMIN, AUDITOR | 200, 401, 403, 404 |
| GET | `/eventos/activos` | Mis eventos en ejecución | Sí: cualquier usuario autenticado | 200, 401 |
| GET | `/eventos/{id}/panel` | Panel del evento en ejecución (rutas, check-in, incidentes) | Sí: cualquier usuario autenticado | 200, 401, 404, 409 |
| PATCH | `/eventos/{id}/finalizar` | Finalizar un evento y evaluarlo | Sí: COORDINADOR, ORGANIZADOR | 200, 401, 403, 404, 409, 503 |
| GET | `/tipos-evento` | Listar los tipos de evento activos | Sí: COORDINADOR, ORGANIZADOR, ADMIN, AUDITOR | 200, 401, 403 |
| POST | `/tipos-evento` | Crear un tipo de evento | Sí: COORDINADOR | 201, 400, 401, 403, 409 |

**Ejemplo de petición**
```http
POST /api/v1/eventos
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "Simulacro de evacuación bloque A",
  "description": "Simulacro semestral",
  "typeIds": ["<id-del-tipo-SIMULACRO>"],
  "startDate": "2026-12-01T15:00:00Z",
  "scope": [{ "type": "ZONE", "id": "z1" }],
  "participantRoles": ["ESTUDIANTE", "PROFESOR"]
}
```

**Formato de errores**
```json
{
  "timestamp": "2026-11-09T09:30:00Z",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "Event not found: 3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42",
  "path": "/api/v1/eventos/3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42",
  "details": []
}
```

Códigos de error (`ErrorCode`): `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `CONFLICT`, `CONCURRENT_MODIFICATION`, `INVALID_STATE_TRANSITION`, `EVENT_NOT_EDITABLE`, `EVENT_NOT_ACTIVE`, `EXTERNAL_SERVICE_UNAVAILABLE` e `INTERNAL_ERROR`.

*(Captura de Swagger UI en `docs/evidencia/swagger.png`.)*

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 🔐 7. Seguridad <a id="seguridad"></a>

- **Autenticación:** JWT sin estado (servidor de recursos OAuth2). Los roles se leen del claim `roles` (configurable con `JWT_ROLES_CLAIM`) y se convierten en autoridades `ROLE_<ROL>`. En perfil `local` hay un decodificador HS256 propio y `GET /local/tokens` entrega un token por rol; fuera de `local` se valida el JWT de MS01 con `spring.security.oauth2.resourceserver.jwt.*`.
- **Autorización:** roles ESTUDIANTE, PROFESOR, ADMINISTRATIVO, BRIGADISTA, COORDINADOR, ORGANIZADOR, ADMIN y AUDITOR (también se aceptan EST, PROF, ADMTV, BRIG, COORD y ORG). El coordinador gestiona eventos reales y el organizador los simulacros; ADMIN y AUDITOR consultan pero no crean, editan ni finalizan.
- **Secretos:** variables de entorno; **ninguno en el repositorio**. El archivo `.env` local está en `.gitignore`.
- **CORS:** orígenes permitidos por `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:5173`).
- **Validación de entrada:** Bean Validation en los DTO (nombre obligatorio de máx. 150 caracteres, descripción de máx. 1000, de 1 a 4 tipos) y validaciones de negocio en los servicios; todos los errores usan `ErrorResponse`.
- **Datos sensibles:** el servicio no guarda contraseñas ni datos personales de los usuarios; solo identificadores de usuario y de brigadistas.
- **Riesgos conocidos:**
  - Los contratos con MS01, MS02, MS03, MS04, MS05 y MS08 siguen siendo propuestas hasta que cada equipo los confirme.
  - La autenticación entre servicios (token o clave de servicio y rol esperado por cada módulo) está por definir; MS03 espera un rol como `SERVICE_MS06`.
  - Un error 4xx de otro módulo hoy se reporta como 503 «no disponible».

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## ⚙️ 8. Configuración y variables de entorno <a id="configuracion"></a>

| Variable | Obligatoria | Ejemplo | Descripción |
|----------|-------------|---------|-------------|
| `DB_URL` | No | `jdbc:postgresql://localhost:5433/campusseguro_events` | URL de la base de datos |
| `DB_USER` | No | `campusseguro` | Usuario de la BD |
| `DB_PASSWORD` | No | `campusseguro` | Contraseña de la BD |
| `SERVER_PORT` | No | `8083` | Puerto del servicio |
| `APP_TIMEZONE` | No | `America/Bogota` | Zona horaria de la aplicación |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173` | Orígenes permitidos por CORS |
| `JWT_ROLES_CLAIM` | No | `roles` | Claim del JWT que trae los roles |
| `JWT_LOCAL_SECRET` | No (solo perfil `local`) | `campusseguro-local-secret-key-change-me-0123456789` | Secreto HS256 de los tokens locales; mínimo 32 caracteres |
| `SERVICE_TOKEN` | No | `<a-long-random-secret>` | Credencial con la que MS06 llama a los otros módulos |
| `MS01_URL` · `MS02_URL` · `MS03_URL` · `MS04_URL` · `MS05_URL` · `MS08_URL` | No | `http://localhost:8084` | URL base de cada módulo (por defecto 8081, 8082, 8080, 8084, 8085 y 8088) |
| `SCHEDULING_ENABLED` | No | `true` | Activa o apaga las tareas periódicas (las pruebas las apagan) |
| `EVENT_START_CHECK_MS` | No | `10000` | Cada cuánto se buscan eventos por iniciar |
| `BEI_SYNC_RUNNING_MS` · `BEI_SYNC_SCHEDULED_MS` | No | `10000` · `30000` | Cada cuánto se copian los brigadistas de eventos en ejecución y programados |
| `EVALUATION_RETRY_MS` | No | `30000` | Cada cuánto se completan las evaluaciones con cifras faltantes |
| `AUDIT_DELIVERY_MS` | No | `15000` | Cada cuánto se envían los registros de auditoría pendientes a MS08 |

> [!IMPORTANT]
> Incluye un `.env.example` sin valores reales. El `.env` real va en `.gitignore`.

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## ▶️ 9. Cómo ejecutarlo <a id="ejecucion"></a>

**Requisitos:** Java 21, Maven 3.9+, Docker Desktop.

```bash
git clone <url-del-repo> && cd ms06-events
cd infra && docker compose up -d && cd ..     # PostgreSQL en el puerto 5433
```
```powershell
$env:DB_PASSWORD = "campusseguro"    # Windows (PowerShell); es el valor por defecto
```
```bash
mvn spring-boot:run
curl http://localhost:8083/local/tokens        # tokens de prueba, confirma que el servicio arrancó
```

El perfil por defecto es `local`: usa los *stubs* de los demás módulos y entrega tokens de prueba en `GET /local/tokens`. El esquema se crea solo al arrancar desde `database/schema.sql`.

**Con Docker**
```bash
docker build -t ms06-events .
docker run -p 8083:8083 --env-file .env ms06-events
```

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 🧪 10. Pruebas y cobertura <a id="pruebas"></a>

**Reporte:** `target/site/jacoco/index.html`

| Tipo | Cantidad | Resultado |
|------|----------|-----------|
| Unitarias | 272 | ✔ |
| Integración | 75 | ✔ |
| **Cobertura total** | | **94%** |

| ID | Escenario | Tipo | Resultado esperado | Estado |
|----|-----------|------|--------------------|--------|
| PT-01 | Crear un evento con fecha, alcance y participantes completos | Unitaria / Integración | Queda programado y con alertas creadas | ✔ |
| PT-02 | Editar un evento programado cambiando la fecha | Unitaria / Integración | Se reprograma la alerta y se avisa a CHI | ✔ |
| PT-03 | Iniciar un evento cuando llega su hora con CHI caído | Integración | El evento inicia y la falla queda guardada para reintento | ✔ |
| PT-04 | Finalizar un evento con CHI o BEI caídos | Unitaria / Integración | Responde 503 y el evento sigue en ejecución | ✔ |
| PT-05 | Finalizar un evento sin fallas | Integración | Queda finalizado con su evaluación guardada | ✔ |
| PT-06 | Buscar eventos por título y estado | Integración | Devuelve la página con las coincidencias o una página vacía | ✔ |
| PT-07 | Un auditor intenta crear o finalizar un evento | Integración | Responde 403 | ✔ |
| PT-08 | MS08 caído al enviar auditoría | Unitaria / Integración | Los registros quedan pendientes y se envían cuando vuelve | ✔ |

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 🚀 11. Infraestructura, despliegue y CI/CD <a id="infraestructura"></a>

- **Entornos:** dev local · **URL:** `http://localhost:8083` (despliegue en la nube: por definir)
- **Contenedores:** PostgreSQL 16 en `infra/docker-compose.yml` (contenedor `ms06-events-postgres`, puerto 5433, volumen `ms06-events-data`). Las pruebas de integración usan Testcontainers con `postgres:16-alpine`.
- **Pipeline:** por definir → `.github/workflows/ci.yml`
- **Etapas:** compilar → pruebas → cobertura (JaCoCo, mínimo 80 % de líneas) → análisis (por definir) → imagen Docker → despliegue
- **Observabilidad:** logs con nivel DEBUG en perfil `local`; Actuator (salud y métricas) aún no está incluido en el proyecto
- **Respaldo y recuperación:** por definir; en local los datos viven en el volumen `ms06-events-data` y los cambios de esquema se aplican con `psql` y luego en `database/schema.sql`

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

## 📁 12. Estructura del proyecto <a id="estructura"></a>

```text
ms06-events/
├── src/main/java/edu/escuelaing/campusseguro/events/
│   ├── controller/     # endpoints REST
│   ├── service/        # lógica de negocio (interfaces)
│   │   └── impl/       # implementaciones de los servicios
│   ├── repository/     # acceso a datos
│   ├── entity/         # entidades JPA
│   ├── dto/            # objetos de entrada (request) y salida (response)
│   ├── mapper/         # conversión entidad → respuesta
│   ├── client/         # gateways hacia MS01, MS02, MS03, MS04, MS05 y MS08
│   ├── scheduler/      # tareas periódicas
│   ├── security/       # roles y política de acceso
│   ├── config/         # seguridad, CORS, OpenAPI, reloj
│   └── exception/      # excepciones y manejador global
├── src/main/resources/ # application.yml, application-local.yml
├── src/test/java/      # pruebas unitarias y de integración
├── database/           # schema.sql (esquema y datos iniciales)
├── infra/              # docker-compose.yml de PostgreSQL
├── docs/
│   ├── wiki/           # páginas de documentación
│   ├── diagrams/       # componentes, clases, ER, infraestructura
│   ├── api/            # openapi.json
│   └── evidencia/      # capturas
├── docker-compose.yml
├── Dockerfile
├── .env.example
└── README.md
```

<p align="right"><a href="#indice">⬆ Volver al índice</a></p>

---

<p align="center"><strong>Licencia:</strong> uso académico</p>