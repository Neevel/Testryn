FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
# The browser talks to the backend directly on the host-mapped port, not through
# this container -- see docker-compose.yml (backend published on localhost:8080).
ENV VITE_API_BASE_URL=http://localhost:8080
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
