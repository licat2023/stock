# 进销存

基于 Spring Boot、MyBatis、MySQL 和 Vue 3 的进销存应用。首次启动后，可以先维护商品和仓库，再登记入库并查看库存。

## Docker 部署

在项目根目录执行以下命令，等待三个服务就绪。

```powershell
docker compose up --build -d --wait
docker compose ps
```

打开 [本机进销存](http://127.0.0.1:18080)。Docker 仅发布本机网页端口，前端通过 Nginx 访问后端，MySQL 数据保存在 Compose 命名卷中。应用默认无需登录。

可将 `.env.example` 复制为 `.env`，调整网页端口和首次初始化的数据库密码。已有数据库卷时，数据库账号密码需与卷内已创建的账号一致。

运行 `docker compose down` 会停止并移除应用容器，保留库存数据。再次按上面的命令启动即可继续使用。

服务器实例通过 [进销存工作台](https://stock.789123465.xyz) 提供访问，需要站点登录口令。已有 1Panel OpenResty 和 MySQL 的服务器可使用[服务器部署配置](deploy/README.md)。

## 业务操作

| 入口 | 操作 |
|---|---|
| 商品、仓库 | 新建、编辑以及删除未被业务单据引用的资料 |
| 入库单 | 选择商品与仓库，填数量、单价、金额中的任意两项，系统补齐并保存库存 |
| 出库单 | 选择商品与仓库，查看库存数量和单价，输入出库数量后预览出库金额 |
| 库存 | 查看各商品在各仓库的结存数量、平均单价和金额 |

库存平均单价为库存金额除以库存数量。数量为正整数，金额保留两位小数，单价保留六位小数。单价或金额为零时，请填写数量。出库金额按库存平均成本的精确比例四舍五入，全部出清时结清剩余金额。

修改、删除历史入库单会重算受影响的历史出库成本和库存快照。更换商品或仓库时会重算原组合及新组合。任一历史时点库存不足时，系统拒绝整次修改。

## 集成验收

以下命令使用独立的 Compose 项目和数据库卷，验收脚本要求 PowerShell 7，并会创建专用测试资料。

```powershell
$env:STOCK_PORT = '18081'
docker compose -p stock-check up --build -d --wait
pwsh -File tests/inventory-smoke.ps1 -BaseUrl http://127.0.0.1:18081 -ComposeProject stock-check
docker compose -p stock-check down
Remove-Item Env:STOCK_PORT
```

接口、实现金额口径与施工验收记录见[任务记录](docs/tasks/T20261117-01-inventory.md)。
