VERSION   := $(shell awk '/<artifactId>hify<\/artifactId>/{f=1} f && /<version>/{gsub(/.*<version>|<\/version>.*/,""); print; exit}' pom.xml)
JAR       := hify-app/target/hify-app-$(VERSION).jar
TARBALL   := hify-$(VERSION).tar.gz
STAGE_DIR := .package-stage

.PHONY: start stop restart build build-backend build-frontend \
        clean clean-backend clean-frontend package help

# ── 启动 / 停止 ──────────────────────────────────────────────────────

start:
	@./start.sh

stop:
	@./stop.sh

restart: stop start

# ── 构建 ─────────────────────────────────────────────────────────────

build: build-backend build-frontend

build-backend:
	@echo ">>> 构建后端..."
	./mvnw clean package -DskipTests -q
	@echo ">>> 后端构建完成：$(JAR)"

build-frontend:
	@echo ">>> 构建前端..."
	cd hify-web && npm run build
	@echo ">>> 前端构建完成：hify-web/dist/"

# ── 清理 ─────────────────────────────────────────────────────────────

clean: clean-backend clean-frontend
	rm -rf $(STAGE_DIR) $(TARBALL)
	@echo ">>> 清理完成"

clean-backend:
	./mvnw clean -q

clean-frontend:
	rm -rf hify-web/dist hify-web/node_modules/.vite

# ── 打包 ─────────────────────────────────────────────────────────────

package: build
	@echo ">>> 打包 $(TARBALL)..."
	@rm -rf $(STAGE_DIR) && mkdir -p $(STAGE_DIR)/hify
	@cp $(JAR) $(STAGE_DIR)/hify/hify-app.jar
	@cp -r hify-web/dist $(STAGE_DIR)/hify/frontend
	@cp start.sh stop.sh Makefile $(STAGE_DIR)/hify/
	@cp -r deploy $(STAGE_DIR)/hify/
	@tar -czf $(TARBALL) -C $(STAGE_DIR) hify
	@rm -rf $(STAGE_DIR)
	@echo ">>> 打包完成：$(TARBALL)"
	@echo "    内容："
	@tar -tzf $(TARBALL) | sed 's/^/      /'

# ── 帮助 ─────────────────────────────────────────────────────────────

help:
	@echo ""
	@echo "  make start          启动后端 + 前端（MySQL/Redis 需先就绪）"
	@echo "  make stop           优雅停止所有服务"
	@echo "  make restart        重启所有服务"
	@echo "  make build          构建后端 + 前端"
	@echo "  make build-backend  仅构建后端（Maven）"
	@echo "  make build-frontend 仅构建前端（Vite）"
	@echo "  make clean          清理所有构建产物"
	@echo "  make package        构建并打包为 $(TARBALL)"
	@echo ""
