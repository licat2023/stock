<!-- Hosts the inventory ledger, document lists, and master data pages. -->
<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  ArrowDownToLine, ArrowUpFromLine, Box, Boxes, ChartNoAxesColumn, ChevronRight,
  CircleAlert, FileText, PackageOpen, Plus, RefreshCw, Search, Wallet, Warehouse,
} from '@lucide/vue'
import { request } from './api.js'
import RecordDialog from './components/RecordDialog.vue'
import { catalogOptions, displayDateTime, displayDecimal, totalAmount } from './components/stockValues.js'

const documents = [
  { key: 'id', label: '单据编号', format: (value) => `#${value}`, class: 'identifier' },
  { key: 'commodityName', label: '商品', class: 'emphasis' },
  { key: 'warehouseName', label: '仓库' },
  { key: 'quantity', label: '数量', numeric: true, format: displayDecimal },
  { key: 'unitPrice', label: '单价（元）', numeric: true, format: (value) => displayDecimal(value, 6) },
  { key: 'amount', label: '金额（元）', numeric: true, format: (value) => displayDecimal(value, 2) },
  { key: 'createdAt', label: '创建时间', format: displayDateTime, class: 'timestamp' },
]

const pages = {
  stocks: {
    name: '库存总览', section: '库存业务', icon: ChartNoAxesColumn,
    description: '按商品和仓库查看当前结存与库存成本。',
    table: '库存明细', combination: true, searchable: ['commodityName', 'warehouseName'],
    placeholder: '搜索商品或仓库', empty: '暂无库存',
    columns: [
      { key: 'commodityName', label: '商品', class: 'emphasis' },
      { key: 'warehouseName', label: '仓库' },
      { key: 'quantity', label: '库存数量', numeric: true, format: displayDecimal },
      { key: 'unitPrice', label: '库存单价（元）', numeric: true, format: (value) => displayDecimal(value, 6) },
      { key: 'amount', label: '库存金额（元）', numeric: true, format: (value) => displayDecimal(value, 2) },
    ],
  },
  inbounds: {
    name: '入库单', section: '库存业务', icon: ArrowDownToLine,
    description: '登记到货，维护入库单据和对应的库存成本。',
    table: '入库记录', combination: true, searchable: ['id', 'commodityName', 'warehouseName'],
    placeholder: '搜索单据编号、商品或仓库', columns: documents,
    create: '新增入库单', empty: '暂无入库记录', next: '新增入库单后，可查询单据和操作前的库存。',
    actions: ['detail', 'edit', 'delete'],
  },
  outbounds: {
    name: '出库单', section: '库存业务', icon: ArrowUpFromLine,
    description: '登记商品出库，按所在仓库的库存成本结算。',
    table: '出库记录', combination: true, searchable: ['id', 'commodityName', 'warehouseName'],
    placeholder: '搜索单据编号、商品或仓库', columns: documents,
    create: '新增出库单', empty: '暂无出库记录', next: '选择商品和仓库，查询可用库存并登记出库。',
    actions: ['detail'],
  },
  commodities: {
    name: '商品管理', section: '基础资料', icon: Box,
    description: '维护单据使用的商品名称与规格。',
    table: '商品列表', searchable: ['id', 'name', 'spec'], placeholder: '搜索商品编号、名称或规格',
    create: '新增商品', empty: '暂无商品', next: '添加商品后，可在出入库单中选用。',
    columns: [
      { key: 'id', label: '商品编号', format: (value) => `#${value}`, class: 'identifier' },
      { key: 'name', label: '商品名称', class: 'emphasis' },
      { key: 'spec', label: '规格' },
    ],
    actions: ['edit', 'delete'],
  },
  warehouses: {
    name: '仓库管理', section: '基础资料', icon: Warehouse,
    description: '维护存放商品的仓库与地址。',
    table: '仓库列表', searchable: ['id', 'name', 'address'], placeholder: '搜索仓库编号、名称或地址',
    create: '新增仓库', empty: '暂无仓库', next: '添加仓库后，可按地点登记收货和发货。',
    columns: [
      { key: 'id', label: '仓库编号', format: (value) => `#${value}`, class: 'identifier' },
      { key: 'name', label: '仓库名称', class: 'emphasis' },
      { key: 'address', label: '地址' },
    ],
    actions: ['edit', 'delete'],
  },
}

const navigation = [
  { label: '库存业务', keys: ['stocks', 'inbounds', 'outbounds'] },
  { label: '基础资料', keys: ['commodities', 'warehouses'] },
]
const actions = { detail: '详情', edit: '编辑', delete: '删除' }
const resources = Object.keys(pages)
const data = reactive(Object.fromEntries(resources.map((kind) => [kind, null])))
const errors = reactive({})
const refreshing = ref(false)
const updatedAt = ref(null)
const current = ref(pageFromHash())
const search = ref('')
const commodityFilter = ref('')
const warehouseFilter = ref('')
const dialog = ref(null)
const notice = ref('')
let refreshController
let noticeTimer

const page = computed(() => pages[current.value])
const headerError = computed(() => errors[current.value])
const hasErrors = computed(() => resources.some((kind) => errors[kind]))
const hasFilters = computed(() => Boolean(search.value || commodityFilter.value || warehouseFilter.value))
const commodityOptions = computed(() => catalogOptions(data.commodities ?? [], 'spec'))
const warehouseOptions = computed(() => catalogOptions(data.warehouses ?? [], 'address'))
const commodityLabels = computed(() => new Map(commodityOptions.value.map(({ id, ledgerLabel }) => [id, ledgerLabel])))
const warehouseLabels = computed(() => new Map(warehouseOptions.value.map(({ id, ledgerLabel }) => [id, ledgerLabel])))
const rows = computed(() => {
  const query = search.value.trim().toLocaleLowerCase('zh-CN').replace(/^#/, '')
  return (data[current.value] ?? []).filter((row) => {
    if (page.value.combination && (
      (commodityFilter.value !== '' && row.commodityId !== commodityFilter.value) ||
      (warehouseFilter.value !== '' && row.warehouseId !== warehouseFilter.value)
    )) return false
    return !query || page.value.searchable.some((key) =>
      String(row[key] ?? '').toLocaleLowerCase('zh-CN').includes(query))
  })
})

const metrics = computed(() => {
  const stock = data.stocks
  return [
    { label: '库存总金额', value: stock && totalAmount(stock), prefix: '¥', icon: Wallet, primary: true },
    { label: '库存总数量', value: stock && stock.reduce((sum, row) => sum + BigInt(row.quantity), 0n), icon: Boxes },
    { label: '在库商品', value: stock && new Set(stock.map((row) => row.commodityId)).size, unit: '种', icon: Box },
    { label: '在库仓库', value: stock && new Set(stock.map((row) => row.warehouseId)).size, unit: '个', icon: Warehouse },
  ]
})

const firstStockStep = computed(() => {
  if (data.commodities?.length === 0) {
    return { kind: 'commodities', text: '先添加商品和仓库，再登记第一笔入库。' }
  }
  if (data.warehouses?.length === 0) return { kind: 'warehouses', text: '添加仓库后，即可为商品登记入库。' }
  return { kind: 'inbounds', text: '登记入库后，可在这里查看库存数量和金额。' }
})

function pageFromHash() {
  const hash = window.location.hash.slice(1)
  return Object.hasOwn(pages, hash) ? hash : 'stocks'
}

function resetFilters() {
  search.value = ''
  commodityFilter.value = ''
  warehouseFilter.value = ''
}

function cellDisplay(row, column) {
  const value = row[column.key]
  if (column.key === 'commodityName') return commodityLabels.value.get(row.commodityId) ?? value
  if (column.key === 'warehouseName') return warehouseLabels.value.get(row.warehouseId) ?? value
  return column.format ? column.format(value) : value || '—'
}

function changePage() {
  const next = pageFromHash()
  if (next === current.value) return
  resetFilters()
  current.value = next
  window.scrollTo({ top: 0 })
}

// Apply each resource independently and ignore any refresh superseded by a write.
async function refresh() {
  refreshController?.abort()
  const controller = new AbortController()
  refreshController = controller
  refreshing.value = true

  await Promise.all(resources.map(async (kind) => {
    try {
      const list = await request(kind, { signal: controller.signal })
      if (!Array.isArray(list)) throw new Error('服务返回的列表格式不正确，请重试。')
      if (controller.signal.aborted) return
      data[kind] = list
      delete errors[kind]
    } catch (error) {
      if (!controller.signal.aborted) errors[kind] = error.message
    }
  }))

  if (!controller.signal.aborted) {
    refreshing.value = false
    if (!hasErrors.value) updatedAt.value = new Date().toISOString()
  }
}

function openDialog(kind, mode = 'create', row) {
  dialog.value = { kind, mode, row }
}

function saved(message) {
  dialog.value = null
  notice.value = message
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { notice.value = '' }, 4000)
  refresh()
}

onMounted(() => {
  window.addEventListener('hashchange', changePage)
  refresh()
})

onUnmounted(() => {
  window.removeEventListener('hashchange', changePage)
  refreshController?.abort()
  clearTimeout(noticeTimer)
})
</script>

<template>
  <a class="skip-link" href="#workspace" @click.prevent="$refs.workspace.focus()">跳到主要内容</a>
  <div class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#stocks">
        <span class="brand-icon"><Boxes aria-hidden="true" :size="23" :stroke-width="1.9" /></span>
        <span>进销存工作台</span>
      </a>

      <nav class="navigation" aria-label="主导航">
        <div v-for="group in navigation" :key="group.label" class="nav-group">
          <div class="nav-label">{{ group.label }}</div>
          <a
            v-for="key in group.keys"
            :key="key"
            :href="`#${key}`"
            :class="['nav-link', { 'is-active': current === key }]"
            :aria-current="current === key ? 'page' : undefined"
          >
            <component :is="pages[key].icon" aria-hidden="true" :size="18" :stroke-width="1.8" />
            {{ pages[key].name }}
          </a>
        </div>
      </nav>

      <div class="sidebar-status" role="status">
        <span :class="['status-dot', { 'is-warning': hasErrors, 'is-pending': refreshing }]" aria-hidden="true"></span>
        {{ refreshing ? '正在同步数据' : hasErrors ? '部分数据加载失败' : '服务已连接' }}
      </div>
    </aside>

    <div class="content-shell">
      <header class="topbar">
        <nav class="breadcrumbs" aria-label="当前位置">
          <span>{{ page.section }}</span>
          <ChevronRight aria-hidden="true" :size="14" />
          <strong>{{ page.name }}</strong>
        </nav>
        <div class="topbar-actions">
          <span v-if="updatedAt" class="last-updated">更新于 {{ displayDateTime(updatedAt) }}</span>
          <button type="button" class="button secondary refresh" :disabled="refreshing" @click="refresh">
            <RefreshCw aria-hidden="true" :size="15" :class="{ spinning: refreshing }" />
            刷新
          </button>
        </div>
      </header>

      <main id="workspace" ref="workspace" class="workspace" tabindex="-1">
        <div class="page-head">
          <div>
            <h1>{{ page.name }}</h1>
            <p>{{ page.description }}</p>
          </div>
          <div class="page-actions">
            <template v-if="current === 'stocks'">
              <button type="button" class="button secondary" @click="openDialog('outbounds')">
                <ArrowUpFromLine aria-hidden="true" :size="17" /> 新增出库单
              </button>
              <button type="button" class="button primary" @click="openDialog('inbounds')">
                <Plus aria-hidden="true" :size="19" /> 新增入库单
              </button>
            </template>
            <button v-else type="button" class="button primary" @click="openDialog(current)">
              <Plus aria-hidden="true" :size="19" /> {{ page.create }}
            </button>
          </div>
        </div>

        <dl v-if="current === 'stocks'" class="metrics" aria-label="全部库存汇总">
          <div v-for="item in metrics" :key="item.label" :class="['metric', { 'metric-primary': item.primary }]">
            <dt>
              <span class="metric-icon"><component :is="item.icon" aria-hidden="true" :size="17" :stroke-width="1.8" /></span>
              {{ item.label }}
            </dt>
            <dd class="number">
              <span v-if="item.prefix && item.value !== null" class="currency">{{ item.prefix }}</span>{{ displayDecimal(item.value) }}
              <span v-if="item.unit && item.value !== null" class="metric-unit">{{ item.unit }}</span>
            </dd>
          </div>
        </dl>

        <section class="ledger" aria-labelledby="ledger-title" :aria-busy="refreshing">
          <div class="ledger-head">
            <div class="ledger-title">
              <h2 id="ledger-title">{{ page.table }}</h2>
              <span v-if="data[current] !== null" class="row-count">{{ rows.length }} 条</span>
            </div>
            <div class="filters">
              <div class="filter-search">
                <Search aria-hidden="true" :size="17" />
                <input v-model="search" :placeholder="page.placeholder" :aria-label="page.placeholder" type="search" class="control">
              </div>
              <template v-if="page.combination">
                <select v-model="commodityFilter" class="control filter-select" aria-label="按商品筛选">
                  <option value="">全部商品</option>
                  <option v-for="item in commodityOptions" :key="item.id" :value="item.id">{{ item.label }}</option>
                </select>
                <select v-model="warehouseFilter" class="control filter-select" aria-label="按仓库筛选">
                  <option value="">全部仓库</option>
                  <option v-for="item in warehouseOptions" :key="item.id" :value="item.id">{{ item.label }}</option>
                </select>
              </template>
              <button v-if="hasFilters" type="button" class="text-button clear-filters" @click="resetFilters">清空</button>
            </div>
          </div>

          <div v-if="headerError" class="load-error" role="alert">
            <CircleAlert aria-hidden="true" :size="19" />
            <p>{{ headerError }} <span v-if="data[current] !== null">当前显示上次加载的数据。</span></p>
            <button type="button" class="text-button" :disabled="refreshing" @click="refresh">重试</button>
          </div>

          <div class="table-scroll" role="region" aria-label="表格" :tabindex="rows.length ? 0 : undefined">
            <table :class="['ledger-table', `table-${current}`]">
              <thead>
                <tr>
                  <th v-for="column in page.columns" :key="column.key" scope="col" :class="{ 'numeric-cell': column.numeric }">
                    {{ column.label }}
                  </th>
                  <th v-if="page.actions" scope="col" class="action-cell">操作</th>
                </tr>
              </thead>
              <tbody v-if="rows.length">
                <tr v-for="row in rows" :key="row.id ?? `${row.commodityId}:${row.warehouseId}`">
                  <td v-for="column in page.columns" :key="column.key" :class="[column.class, { 'numeric-cell number': column.numeric }]">
                    {{ cellDisplay(row, column) }}
                  </td>
                  <td v-if="page.actions" class="action-cell">
                    <button
                      v-for="action in page.actions"
                      :key="action"
                      type="button"
                      :class="['text-button', { 'delete-action': action === 'delete' }]"
                      :aria-label="`${actions[action]}${row.name ?? `单据 #${row.id}`}`"
                      @click="openDialog(current, action, row)"
                    >{{ actions[action] }}</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="!rows.length" class="empty-state">
            <template v-if="data[current] === null">
              <RefreshCw v-if="refreshing" class="empty-loader spinning" aria-hidden="true" :size="25" />
              <h3>{{ refreshing ? '正在加载…' : '暂时无法查看数据' }}</h3>
            </template>
            <template v-else>
              <div class="empty-symbol">
                <Search v-if="hasFilters" aria-hidden="true" :size="29" :stroke-width="1.45" />
                <PackageOpen v-else-if="current === 'stocks'" aria-hidden="true" :size="32" :stroke-width="1.45" />
                <FileText v-else-if="page.combination" aria-hidden="true" :size="30" :stroke-width="1.45" />
                <component :is="page.icon" v-else aria-hidden="true" :size="31" :stroke-width="1.45" />
              </div>
              <h3>{{ hasFilters ? '没有匹配的记录' : page.empty }}</h3>
              <p>{{ hasFilters ? '尝试调整搜索词或筛选条件。' : current === 'stocks' ? firstStockStep.text : page.next }}</p>
              <button v-if="hasFilters" type="button" class="button secondary" @click="resetFilters">清空筛选</button>
              <button v-else-if="current === 'stocks'" type="button" class="button secondary" @click="openDialog(firstStockStep.kind)">
                <Plus aria-hidden="true" :size="17" /> {{ pages[firstStockStep.kind].create }}
              </button>
            </template>
          </div>
        </section>
      </main>
    </div>
  </div>

  <div class="notice" role="status" aria-live="polite"><span v-if="notice">{{ notice }}</span></div>
  <RecordDialog
    v-if="dialog"
    :dialog="dialog"
    :commodities="commodityOptions"
    :warehouses="warehouseOptions"
    @close="dialog = null"
    @saved="saved"
  />
</template>
