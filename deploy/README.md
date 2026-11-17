# 服务器部署

该配置在已有 1Panel OpenResty 和 MySQL 8.4 的主机上运行进销存。OpenResty 托管 Vue 文件并反代 `/api/`。后端只绑定主机回环端口，通过 `1panel-network` 连接 MySQL。

## 部署布局

| 路径 | 用途 |
|---|---|
| `/opt/stock-inventory/compose.yaml` | 后端容器与资源上限 |
| `/opt/stock-inventory/.env` | 镜像名 `STOCK_IMAGE` 与专用数据库密码 `STOCK_DB_PASSWORD`，文件权限为 0600 |
| `/opt/1panel/www/sites/stock.789123465.xyz/index/` | Vue 生产文件 |
| `/opt/1panel/www/sites/stock.789123465.xyz/access.htpasswd` | OpenResty 访问口令哈希 |
| `/opt/1panel/www/conf.d/stock.789123465.xyz.conf` | 站点配置，由本目录模板替换证书路径生成 |

## 更新步骤

1. 用 `docker build -t stock-inventory-backend:<版本> ./backend` 构建后端镜像。服务器无法直连镜像源时，以 `docker save` 导出、传输后在服务器用 `docker load` 导入。
2. 在 `frontend/` 执行 `npm ci` 和 `npm run build`。将 `dist/` 内容同步到服务器站点的 `index/`，先同步带哈希的资源，最后替换 `index.html`。
3. 在 MySQL 建立 `stock_inventory` 数据库和 `stock_web` 用户。应用初始化和运行需要该库的 `CREATE`、`SELECT`、`INSERT`、`UPDATE`、`DELETE`、`REFERENCES` 权限。设置服务器 `.env` 后，在 `/opt/stock-inventory` 执行 `docker compose up -d --wait`。
4. 为站点准备证书和口令哈希文件，将模板中的 `@CERTIFICATE@` 和 `@CERTIFICATE_KEY@` 换成 OpenResty 容器内路径。启用了 1Panel WAF 的主机，还需按现有格式将该域名登记到 WAF 的站点清单 `1pwaf/data/conf/sites.json`。运行 `docker exec <OpenResty容器> openresty -t`，通过后运行 `docker exec <OpenResty容器> openresty -s reload`。
5. 验证 HTTPS 站点。直接访问应提示登录，使用授权口令后应能打开页面、读取库存及创建入库单。

数据库及口令由服务器独立维护。应用更新时保留数据库和一并保存的服务器环境配置。

线上使用站点自有的 Let's Encrypt 证书。现有 `acme.sh` 定时任务负责续期，验证目录为站点下的 `acme/`，续期成功后检查配置并重载 OpenResty。OpenResty 站点由配置文件维护；若面板重建 WAF 站点清单，检查该域名仍在清单中。
