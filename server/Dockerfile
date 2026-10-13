# qMusic 音乐抓取服务 · 容器镜像
#
# 多阶段构建：
#   1) golang 镜像编译多音源服务（musiclib-service → qmusic-musiclib）
#   2) node 镜像安装网关依赖，与 Go 产物合成最终镜像
#
# 运行方式（Render / Railway / Koyeb 等任意容器平台）：
#   - 平台注入 PORT（默认 10000），网关自动跟随
#   - MUSICLIB_AUTOSTART=1：由网关进程顺带拉起 Go 服务（同容器双进程）
#   - 健康检查：GET /

# ---------- 阶段 1：编译 Go 多音源服务 ----------
FROM golang:1.26 AS go-builder

WORKDIR /src/musiclib-service
COPY musiclib-service/ .

ENV GOPROXY=https://goproxy.cn,direct
ENV GOSUMDB=off
ENV GOTOOLCHAIN=local
ENV CGO_ENABLED=0

RUN go build -trimpath -ldflags="-s -w" -o /out/qmusic-musiclib .

# ---------- 阶段 2：Node 网关 ----------
FROM node:22-slim

ENV NODE_ENV=production
WORKDIR /app

# 先装依赖（层缓存）：package-lock.json 保证可复现安装
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

# 网关源码
COPY index.js multi-source.js ./

# Go 多音源产物（go build 产物自带可执行位，COPY 保留）
COPY --from=go-builder /out/qmusic-musiclib ./musiclib-service/qmusic-musiclib

# 运行时配置：
#   PORT             平台注入（Render 默认 10000）
#   NCM_PORT         网易云接口服务内网端口
#   MUSICLIB_AUTOSTART  网关启动时拉起 Go 服务
#   MUSICLIB_URL     网关访问 Go 服务的地址
ENV NCM_PORT=3001 \
    MUSICLIB_AUTOSTART=1 \
    MUSICLIB_URL=http://127.0.0.1:3100 \
    MUSICLIB_PORT=3100

EXPOSE 3000 10000

CMD ["node", "index.js"]
