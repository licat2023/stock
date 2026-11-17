<!-- Edit the enterable values of an inbound record while displaying its calculated value. -->
<script setup>
defineProps({ fields: Object, evaluation: Object })
const emit = defineEmits(['change'])

const inputs = [
  { key: 'quantity', label: '数量', mode: 'numeric', limit: 10, hint: '输入整数' },
  { key: 'unitPrice', label: '单价（元）', mode: 'decimal', limit: 25, hint: '最多 6 位小数' },
  { key: 'amount', label: '金额（元）', mode: 'decimal', limit: 21, hint: '最多 2 位小数' },
]
</script>

<template>
  <div class="numeric-fields">
    <div v-for="field in inputs" :key="field.key" class="field">
      <label class="field-label" :for="`record-${field.key}`">
        {{ field.label }}
        <span v-if="evaluation.preview?.key === field.key" class="calculated">自动计算</span>
      </label>
      <input
        :id="`record-${field.key}`"
        class="control numeric-control"
        :class="{ 'is-calculated': evaluation.preview?.key === field.key }"
        :inputmode="field.mode"
        :maxlength="field.limit"
        :placeholder="evaluation.preview?.key === field.key ? evaluation.preview.value : field.hint"
        :value="fields[field.key]"
        aria-describedby="inbound-value-hint"
        @input="emit('change', field.key, $event.target.value)"
      >
    </div>
  </div>
  <p id="inbound-value-hint" class="field-hint">任意填写两项可自动计算第三项，点进数值框可改为手填。</p>
  <p v-if="evaluation.error" class="field-error" role="status">{{ evaluation.error }}</p>
</template>
