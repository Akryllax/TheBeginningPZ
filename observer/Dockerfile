FROM docker.io/library/node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS frontend
WORKDIR /build
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM docker.io/library/python:3.12-slim@sha256:f77ac9e44ae96ef2c90b8053ea08c31f8be030f824196b0ae4db6d462c84e51f
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 OBSERVER_DATA=/data OBSERVER_FRONTEND=/app/web/dist OBSERVER_MOD_ZIP=/app/ZomboidObserver.zip
WORKDIR /app
COPY requirements.lock ./
RUN pip install --no-cache-dir --require-hashes -r requirements.lock
COPY observer/ ./observer/
COPY scripts/package_mod.py ./scripts/package_mod.py
COPY mod/ ./mod/
RUN python scripts/package_mod.py /app/ZomboidObserver.zip
COPY --from=frontend /build/dist ./web/dist/
USER 1001:1001
EXPOSE 8000
CMD ["uvicorn","observer.app:create_app","--factory","--host","0.0.0.0","--port","8000","--proxy-headers","--forwarded-allow-ips=*","--no-access-log"]
