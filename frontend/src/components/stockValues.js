// Keeps document calculations and ledger displays exact at the API's decimal scales.

/** Formats master-data options so identical names can still be distinguished. */
export function catalogOptions(rows, attribute) {
  const counts = new Map()
  for (const { name } of rows) counts.set(name, (counts.get(name) ?? 0) + 1)

  return rows.map((row) => {
    const description = row[attribute] ? `（${row[attribute]}）` : ''
    const identifier = counts.get(row.name) > 1 ? ` #${row.id}` : ''
    const label = `${row.name}${identifier}${description}`
    return { id: row.id, label, ledgerLabel: identifier ? label : row.name }
  })
}
const fields = ['quantity', 'unitPrice', 'amount']
const maximumQuantity = 2_147_483_647n
const maximumInteger = 10n ** 18n
const scales = { quantity: 0, unitPrice: 6, amount: 2 }
const fieldNames = { quantity: '数量', unitPrice: '单价', amount: '金额' }
const groupedInteger = new Intl.NumberFormat('zh-CN')
const localDateTime = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short', timeStyle: 'medium', hourCycle: 'h23',
})

function parseDecimal(value, scale) {
  if (!/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) return null
  const [whole, fraction = ''] = value.split('.')
  if (fraction.length > scale) return null
  return BigInt(whole + fraction.padEnd(scale, '0'))
}

function fixedDecimal(units, scale) {
  const digits = units.toString().padStart(scale + 1, '0')
  return scale === 0 ? digits : `${digits.slice(0, -scale)}.${digits.slice(-scale)}`
}

function roundedDivision(numerator, denominator) {
  return (numerator + denominator / 2n) / denominator
}

/** Returns a valid API quantity or null while the field is empty or invalid. */
export function readQuantity(value) {
  const digits = String(value).trim()
  if (!/^\d+$/.test(digits)) return null
  const quantity = BigInt(digits)
  return quantity > 0n && quantity <= maximumQuantity ? Number(quantity) : null
}

/** Evaluates the draft without inserting the derived value into the saved payload. */
export function inboundValues(draft) {
  const values = {}
  const body = {}
  for (const key of fields) {
    const input = String(draft[key] ?? '').trim()
    if (input === '') continue

    const scale = scales[key]
    if (key === 'quantity') {
      const quantity = readQuantity(input)
      if (quantity === null) return { error: '数量须为 1 至 2,147,483,647 的整数。' }
      values[key] = BigInt(quantity)
      body[key] = quantity
    } else {
      const value = parseDecimal(input, scale)
      if (value === null || value >= maximumInteger * 10n ** BigInt(scale)) {
        return { error: `${fieldNames[key]}须为非负数，最多 18 位整数和 ${scale} 位小数。` }
      }
      values[key] = value
      body[key] = fixedDecimal(value, scale)
    }
  }

  const { quantity, unitPrice, amount } = values
  const inputCount = Object.keys(body).length
  if (inputCount < 2) return {}

  let preview
  if (quantity === undefined) {
    if (unitPrice === 0n || amount === 0n) return { error: '单价或金额为零时，请手动填写数量。' }
    const calculated = roundedDivision(amount * 10_000n, unitPrice)
    if (calculated < 1n || calculated > maximumQuantity ||
        roundedDivision(calculated * unitPrice, 10_000n) !== amount) {
      return { error: '输入的单价和金额无法计算出有效的整数数量，请调整数值。' }
    }
    preview = { key: 'quantity', value: String(calculated) }
  } else if (unitPrice === undefined) {
    const calculated = roundedDivision(amount * 10_000n, quantity)
    preview = { key: 'unitPrice', value: fixedDecimal(calculated, 6) }
  } else if (amount === undefined) {
    const calculated = roundedDivision(quantity * unitPrice, 10_000n)
    if (calculated >= maximumInteger * 100n) return { error: '计算出的金额超过可保存的范围。' }
    preview = { key: 'amount', value: fixedDecimal(calculated, 2) }
  } else if (roundedDivision(quantity * unitPrice, 10_000n) !== amount &&
             roundedDivision(amount * 10_000n, quantity) !== unitPrice) {
    return { error: '数量、单价和金额不一致，可清空其中一项重新计算。' }
  }

  return { body, preview }
}

/** Prefills two fields that reproduce the saved values, including amount-derived unit prices. */
export function editInboundFields(record) {
  const quantity = String(record.quantity)
  const unitPrice = String(record.unitPrice)
  const amount = String(record.amount)
  const fromUnitPrice = inboundValues({ quantity, unitPrice }).preview

  return fromUnitPrice?.value === fixedDecimal(parseDecimal(amount, 2), 2)
    ? { quantity, unitPrice, amount: '' }
    : { quantity, unitPrice: '', amount }
}

/** Sums exact decimal amounts without converting to binary floating point. */
export function totalAmount(rows) {
  const cents = rows.reduce((sum, row) => sum + parseDecimal(String(row.amount), 2), 0n)
  return fixedDecimal(cents, 2)
}

/** Preserves the decimal digits supplied by the API, with localized digit grouping. */
export function displayDecimal(value, minimumFractionDigits = 0) {
  if (value === null || value === undefined) return '—'
  const [whole, fraction = ''] = String(value).split('.')
  const digits = fraction.padEnd(minimumFractionDigits, '0')
  const integer = groupedInteger.format(BigInt(whole))
  return digits ? `${integer}.${digits}` : integer
}

/** Displays API timestamps in the browser's local time zone. */
export function displayDateTime(value) {
  const date = new Date(value)
  return value && Number.isFinite(date.getTime()) ? localDateTime.format(date) : '—'
}
