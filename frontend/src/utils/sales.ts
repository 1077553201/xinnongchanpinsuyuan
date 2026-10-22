/** 人民币按分计算，不用浮点运算推测订单金额。 */
export function salesAmount(lines: { quantity: string | number; price: string | number }[]): string | null {
  if (!lines.length || lines.length > 50) return null
  let total = 0n
  for (const line of lines) {
    const quantity = String(line.quantity), price = String(line.price)
    if (!/^\d{1,7}$/.test(quantity) || Number(quantity) < 1 || Number(quantity) > 1000000 || !/^\d{1,9}(\.\d{1,2})?$/.test(price)) return null
    const [whole, fraction = ''] = price.split('.')
    const cents = BigInt(whole!) * 100n + BigInt(fraction.padEnd(2, '0'))
    total += BigInt(quantity) * cents
  }
  return `${total / 100n}.${String(total % 100n).padStart(2, '0')}`
}
