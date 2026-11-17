<!-- Presents modal forms and details for master records and inventory documents. -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowDownToLine, ArrowUpFromLine, Box, FileText, Trash2, Warehouse, X } from '@lucide/vue'
import { request } from '../api.js'
import InboundFields from './InboundFields.vue'
import { displayDateTime, displayDecimal, editInboundFields, inboundValues, readQuantity } from './stockValues.js'

const props = defineProps({ dialog: Object, commodities: Array, warehouses: Array })
const emit = defineEmits(['close', 'saved'])
const modal = ref(null)
const saving = ref(false)
const formError = ref('')
const { kind, mode, row } = props.dialog
const config = {
  commodities: { label: '商品', attribute: 'spec', attributeLabel: '规格', icon: Box },
  warehouses: { label: '仓库', attribute: 'address', attributeLabel: '地址', icon: Warehouse },
  inbounds: { label: '入库单', icon: ArrowDownToLine },
  outbounds: { label: '出库单', icon: ArrowUpFromLine },
}[kind]

const action = { create: '新增', edit: '编辑', delete: '删除', detail: '' }[mode]
const title = mode === 'detail' ? `${config.label}详情` : `${action}${config.label}`
const titleIcon = mode === 'delete' ? Trash2 : mode === 'detail' ? FileText : config.icon
const warehouseDocument = kind === 'inbounds' || kind === 'outbounds'
const inbound = kind === 'inbounds'
const quoteRequired = kind === 'outbounds' && mode === 'create'
const commodityLabel = computed(() => props.commodities.find((item) => item.id === row?.commodityId)?.ledgerLabel ?? row?.commodityName)
const warehouseLabel = computed(() => props.warehouses.find((item) => item.id === row?.warehouseId)?.ledgerLabel ?? row?.warehouseName)
const values = inbound && row && mode === 'edit'
  ? editInboundFields(row)
  : { quantity: '', unitPrice: '', amount: '' }

const draft = reactive({
  name: row?.name ?? '',
  attribute: row?.[config.attribute] ?? '',
  commodityId: row?.commodityId ?? '',
  warehouseId: row?.warehouseId ?? '',
  ...values,
})

const evaluation = computed(() => inboundValues(draft))
const quantity = computed(() => readQuantity(draft.quantity))
const stockSelection = computed(() => {
  if (!quoteRequired || draft.commodityId === '' || draft.warehouseId === '') return ''
  return new URLSearchParams({ commodityId: draft.commodityId, warehouseId: draft.warehouseId }).toString()
})

const quoteRevision = ref(0)
const baseQuote = ref(null)
const amountQuote = ref(null)
const baseError = ref('')
const amountError = ref('')
const loadingBase = ref(false)
const loadingAmount = ref(false)

watch([stockSelection, quoteRevision], async ([selection], _, onCleanup) => {
  baseQuote.value = null
  baseError.value = ''
  loadingBase.value = Boolean(selection)
  if (!selection) return

  const controller = new AbortController()
  onCleanup(() => controller.abort())
  try {
    const result = await request(`outbounds/quote?${selection}`, { signal: controller.signal })
    if (!controller.signal.aborted) baseQuote.value = result
  } catch (error) {
    if (!controller.signal.aborted) baseError.value = error.message
  } finally {
    if (!controller.signal.aborted) loadingBase.value = false
  }
})

// A combination change or a new quantity invalidates the previous amount before requesting another.
watch([stockSelection, quantity, baseQuote], ([selection, count, base], _, onCleanup) => {
  amountQuote.value = null
  amountError.value = ''
  loadingAmount.value = Boolean(selection && count !== null && base)
  if (!loadingAmount.value) return

  const controller = new AbortController()
  const timer = setTimeout(async () => {
    try {
      const result = await request(`outbounds/quote?${selection}&quantity=${count}`, { signal: controller.signal })
      if (!controller.signal.aborted) amountQuote.value = result
    } catch (error) {
      if (!controller.signal.aborted) amountError.value = error.message
    } finally {
      if (!controller.signal.aborted) loadingAmount.value = false
    }
  }, 180)
  onCleanup(() => {
    clearTimeout(timer)
    controller.abort()
  })
})

const displayedQuote = computed(() => amountQuote.value ?? baseQuote.value)
const quoteError = computed(() => baseError.value || amountError.value)

onMounted(() => modal.value.showModal())

function close() {
  if (saving.value) return
  modal.value.close()
  emit('close')
}

function updateField(key, value) {
  draft[key] = value
  formError.value = ''
}

async function submit() {
  if (saving.value) return
  formError.value = ''

  let body
  if (mode !== 'delete') {
    if (warehouseDocument) {
      const documentValues = inbound ? evaluation.value.body : { quantity: quantity.value }
      if (!documentValues) {
        if (!evaluation.value.error) formError.value = '请至少填写数量、单价和金额中的两项。'
        return
      }
      if (quoteRequired && !amountQuote.value) return
      body = { commodityId: draft.commodityId, warehouseId: draft.warehouseId, ...documentValues }
    } else {
      const name = draft.name.trim()
      if (!name) {
        formError.value = `请输入${config.label}名称。`
        return
      }
      body = { name, [config.attribute]: draft.attribute.trim() }
    }
  }

  const method = { create: 'POST', edit: 'PUT', delete: 'DELETE' }[mode]
  const path = row ? `${kind}/${row.id}` : kind
  saving.value = true
  try {
    await request(path, { method, body })
    modal.value.close()
    emit('saved', `${config.label}已${action === '编辑' ? '更新' : action}`)
  } catch (error) {
    formError.value = error.message
    if (quoteRequired && error.status === 409) quoteRevision.value += 1
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <dialog ref="modal" class="record-dialog" aria-labelledby="record-title" @cancel.prevent="close">
    <form @submit.prevent="submit">
      <header class="dialog-head">
        <div class="dialog-title">
          <span :class="['dialog-symbol', { 'is-danger': mode === 'delete' }]">
            <component :is="titleIcon" aria-hidden="true" :size="21" />
          </span>
          <h2 id="record-title">{{ title }}</h2>
        </div>
        <button type="button" class="icon-button" aria-label="关闭" :disabled="saving" @click="close">
          <X aria-hidden="true" :size="21" />
        </button>
      </header>

      <div v-if="mode === 'delete'" class="dialog-body">
        <p class="confirm-target">
          {{ warehouseDocument ? `#${row.id} · ${commodityLabel} / ${warehouseLabel}` : row.name }}
        </p>
        <p class="dialog-help">
          {{ inbound
            ? '删除后会重新计算相关库存和历史单据。如果造成某一时点库存不足，系统会拒绝删除。'
            : '已有入库、出库或库存业务引用时，系统会拒绝删除。' }}
        </p>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </div>

      <div v-else-if="mode === 'detail'" class="dialog-body">
        <dl class="details-grid">
          <div><dt>单据编号</dt><dd class="number">#{{ row.id }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ displayDateTime(row.createdAt) }}</dd></div>
          <div><dt>商品</dt><dd>{{ commodityLabel }}</dd></div>
          <div><dt>仓库</dt><dd>{{ warehouseLabel }}</dd></div>
        </dl>
        <section class="snapshot" aria-labelledby="snapshot-title">
          <h3 id="snapshot-title">单据与操作前库存</h3>
          <table class="snapshot-table">
            <thead><tr><th scope="col">项目</th><th scope="col">本单</th><th scope="col">操作前库存</th></tr></thead>
            <tbody>
              <tr><th scope="row">数量</th><td>{{ displayDecimal(row.quantity) }}</td><td>{{ displayDecimal(row.previousQuantity) }}</td></tr>
              <tr><th scope="row">单价（元）</th><td>{{ displayDecimal(row.unitPrice, 6) }}</td><td>{{ displayDecimal(row.previousUnitPrice, 6) }}</td></tr>
              <tr><th scope="row">金额（元）</th><td>{{ displayDecimal(row.amount, 2) }}</td><td>{{ displayDecimal(row.previousAmount, 2) }}</td></tr>
            </tbody>
          </table>
        </section>
      </div>

      <fieldset v-else class="dialog-body fields" :disabled="saving">
        <template v-if="warehouseDocument">
          <div class="record-selects">
            <div class="field">
              <label class="field-label" for="record-commodity">商品</label>
              <select id="record-commodity" v-model="draft.commodityId" class="control" required autofocus>
                <option disabled value="">请选择商品</option>
                <option v-for="item in commodities" :key="item.id" :value="item.id">
                  {{ item.label }}
                </option>
              </select>
            </div>
            <div class="field">
              <label class="field-label" for="record-warehouse">仓库</label>
              <select id="record-warehouse" v-model="draft.warehouseId" class="control" required>
                <option disabled value="">请选择仓库</option>
                <option v-for="item in warehouses" :key="item.id" :value="item.id">{{ item.label }}</option>
              </select>
            </div>
          </div>
          <p v-if="commodities.length === 0 || warehouses.length === 0" class="field-hint">
            请先在基础资料中添加商品和仓库。
          </p>
          <InboundFields v-if="inbound" :fields="draft" :evaluation="evaluation" @change="updateField" />
          <template v-else>
            <div class="field">
              <label class="field-label" for="record-outbound-quantity">出库数量</label>
              <input
                id="record-outbound-quantity"
                class="control numeric-control"
                :value="draft.quantity"
                inputmode="numeric"
                maxlength="10"
                placeholder="输入整数"
                required
                @input="updateField('quantity', $event.target.value)"
              >
              <p v-if="draft.quantity && quantity === null" class="field-error" role="status">
                数量须为 1 至 2,147,483,647 的整数。
              </p>
            </div>
            <div class="quote" aria-live="polite" :aria-busy="loadingBase || loadingAmount">
              <dl class="quote-values">
                <div>
                  <dt>可用库存</dt>
                  <dd>{{ displayDecimal(displayedQuote?.availableQuantity) }}</dd>
                </div>
                <div>
                  <dt>库存单价（元）</dt>
                  <dd>{{ displayDecimal(displayedQuote?.unitPrice, 6) }}</dd>
                </div>
                <div>
                  <dt>出库金额（元）</dt>
                  <dd>{{ displayDecimal(amountQuote?.amount, 2) }}</dd>
                </div>
              </dl>
              <p v-if="!stockSelection" class="field-hint">选择商品和仓库后显示库存单价。</p>
              <p v-else-if="loadingBase || loadingAmount" class="field-hint">正在计算库存成本…</p>
              <div v-else-if="quoteError" class="quote-error">
                <p class="field-error">{{ quoteError }}</p>
                <button type="button" class="text-button" @click="quoteRevision++">重新取价</button>
              </div>
              <p v-else-if="displayedQuote?.availableQuantity === 0" class="field-hint">当前仓库无可用库存。</p>
            </div>
          </template>
        </template>
        <template v-else>
          <div class="field">
            <label class="field-label" for="record-name">{{ config.label }}名称</label>
            <input id="record-name" v-model="draft.name" class="control" maxlength="255" required autofocus>
          </div>
          <div class="field">
            <label class="field-label" for="record-attribute">{{ config.attributeLabel }} <span class="optional">选填</span></label>
            <input id="record-attribute" v-model="draft.attribute" class="control" maxlength="255">
          </div>
        </template>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </fieldset>

      <footer class="dialog-foot">
        <button v-if="mode === 'detail'" type="button" class="button secondary" autofocus @click="close">关闭</button>
        <template v-else>
          <button type="button" class="button secondary" :disabled="saving" :autofocus="mode === 'delete'" @click="close">取消</button>
          <button
            type="submit"
            :class="['button', mode === 'delete' ? 'danger' : 'primary']"
            :disabled="saving || (quoteRequired && !amountQuote)"
          >
            {{ saving ? '正在处理…' : mode === 'delete' ? '确认删除' : '保存' }}
          </button>
        </template>
      </footer>
    </form>
  </dialog>
</template>
