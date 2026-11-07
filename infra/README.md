# Base de datos local de MS06

PostgreSQL 16 en Docker. Los valores por defecto coinciden con `application-local.yml`
(`localhost:5433`, base `campusseguro_events`, usuario y clave `campusseguro`), así que la
aplicación conecta sin configurar nada. Para cambiarlos: `cp .env.example .env` y edita.

Todos los comandos se ejecutan desde la carpeta `infra/`.

## Levantar (un solo comando)

```bash
docker compose up -d
```

Descarga la imagen la primera vez, crea el contenedor `ms06-events-postgres` y, al crear la
base por primera vez, ejecuta `../database/schema.sql` (tablas y los 7 tipos de evento).
Para ver que esté listo: `docker compose ps` (estado `healthy`).

## Detener

```bash
docker compose stop        # detiene; conserva los datos
docker compose start       # lo vuelve a iniciar
docker compose down        # elimina el contenedor; conserva los datos (volumen)
docker compose down -v     # elimina contenedor Y datos (base desde cero)
```

## Entrar a la base (psql)

```bash
docker exec -it ms06-events-postgres psql -U campusseguro -d campusseguro_events
```

Dentro de psql: `\dt` lista las tablas, `SELECT code, name FROM event_types;` muestra los
tipos y `\q` sale.

## Comandos manuales (equivalentes, sin compose)

Útil si la base ya existía antes del script o si cambias `database/schema.sql`:

```bash
docker cp ../database/schema.sql ms06-events-postgres:/schema.sql
docker exec -i ms06-events-postgres psql -U campusseguro -d campusseguro_events -f /schema.sql
```

El script es idempotente: se puede ejecutar varias veces sin dañar datos. Además, la
aplicación lo ejecuta sola en cada arranque, así que estos comandos son opcionales.

## Notas

- Los scripts de `/docker-entrypoint-initdb.d` solo corren cuando el volumen está vacío
  (primera vez o después de `down -v`).
- Si el puerto 5433 está ocupado, cambia `DB_PORT` en `.env` y usa `DB_URL` al correr la aplicación.
