/**
 * 前端设计预览用的 mock 服务（开发辅助，不参与生产）。
 *
 * 用途：在后端 / MySQL 未启动时，让前端页面能拿到结构真实的响应，
 * 便于评审版式与视觉。响应结构与后端 org.nongchanpin 的 VO/DTO 保持一致。
 *
 * 启动：npm run mock   （默认监听 8090；配合 VITE_PROXY_TARGET 使用）
 */
import { createServer } from 'node:http'

const PORT = Number(process.env.MOCK_PORT ?? 8090)

function hash(seed, length = 64) {
  let out = ''
  let x = 0
  for (let i = 0; i < seed.length; i += 1) x = (x * 31 + seed.charCodeAt(i)) >>> 0
  while (out.length < length) {
    x = (x * 1103515245 + 12345) >>> 0
    out += (x >>> 8).toString(16).padStart(6, '0')
  }
  return out.slice(0, length)
}

function ok(data) {
  return { code: '0', message: '', data }
}

function fail(code, message) {
  return { code, message, data: null }
}

const CONTRACT = '0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94'

function tracePayload(code) {
  return {
    product: {
      productCode: 'P-WALNUT-001',
      productName: '临安山核桃',
      category: '坚果',
      variety: '薄壳山核桃',
      originPlace: '浙江省杭州市临安区',
      description: '海拔 600 米山地种植，人工采收后 24 小时内低温烘干。',
    },
    batch: {
      batchCode: 'B-20260912-01',
      productionArea: '临安基地 A3 地块',
      plantDate: '2026-03-18',
      harvestDate: '2026-09-12',
      chainStatus: 'CONFIRMED',
      status: 'ACTIVE',
    },
    item: {
      traceCodeHash: hash(`item-${code}`),
      traceUrl: `http://127.0.0.1:5173/t/${code}`,
      qrStatus: 'GENERATED',
      nfcStatus: 'BOUND',
      chainStatus: 'CONFIRMED',
      status: 'ACTIVE',
    },
    nfc: {
      uidHash: hash('nfc-0501a3', 64),
      bindStatus: 'BOUND',
      status: 'ACTIVE',
    },
    timeline: [
      { eventType: 'SOWING', eventTime: '2026-03-18 09:20:00', location: '临安基地 A3 地块', operatorName: '张伟', payloadHash: hash('ev-sow'), chainStatus: 'CONFIRMED' },
      { eventType: 'FARMING', eventTime: '2026-05-06 07:40:00', location: '临安基地 A3 地块', operatorName: '张伟', payloadHash: hash('ev-farm1'), chainStatus: 'CONFIRMED' },
      { eventType: 'FARMING', eventTime: '2026-07-22 16:05:00', location: '临安基地 A3 地块', operatorName: '李红', payloadHash: hash('ev-farm2'), chainStatus: 'CONFIRMED' },
      { eventType: 'HARVEST', eventTime: '2026-09-12 06:30:00', location: '临安基地 A3 地块', operatorName: '张伟', payloadHash: hash('ev-harvest'), chainStatus: 'CONFIRMED' },
      { eventType: 'PROCESSING', eventTime: '2026-09-12 15:10:00', location: '临安初加工中心', operatorName: '王强', payloadHash: hash('ev-process'), chainStatus: 'CONFIRMED' },
      { eventType: 'QUALITY_TEST', eventTime: '2026-09-14 10:00:00', location: '杭州农产品检测中心', operatorName: '检测中心', payloadHash: hash('ev-qc'), chainStatus: 'CONFIRMED' },
      { eventType: 'WAREHOUSE_IN', eventTime: '2026-09-15 08:15:00', location: '临安中心仓 C2', operatorName: '周敏', payloadHash: hash('ev-wh'), chainStatus: 'CONFIRMED' },
      { eventType: 'LOGISTICS', eventTime: '2026-09-20 13:45:00', location: '临安 → 上海', operatorName: '顺捷物流', payloadHash: hash('ev-logi'), chainStatus: 'PENDING' },
    ],
    qualityReports: [
      { reportNo: 'QC-2026-0914-118', agencyName: '杭州农产品质量检测中心', result: 'PASS', reportHash: hash('qc-118'), checkedAt: '2026-09-14' },
    ],
    environmentSummary: [
      { deviceCode: 'CAR-A3-01', metric: 'temperature', windowStart: '2026-08-01 00:00:00', windowEnd: '2026-08-01 06:00:00', minValue: '18.400000', maxValue: '27.900000', avgValue: '23.150000', sampleCount: 288, dataHash: hash('w-temp'), chainStatus: 'CONFIRMED' },
      { deviceCode: 'CAR-A3-01', metric: 'humidity', windowStart: '2026-08-01 00:00:00', windowEnd: '2026-08-01 06:00:00', minValue: '61.000000', maxValue: '88.500000', avgValue: '74.200000', sampleCount: 288, dataHash: hash('w-hum'), chainStatus: 'CONFIRMED' },
      { deviceCode: 'CAR-A3-01', metric: 'soil_moisture', windowStart: '2026-08-01 00:00:00', windowEnd: '2026-08-01 06:00:00', minValue: '32.000000', maxValue: '48.000000', avgValue: '39.600000', sampleCount: 288, dataHash: hash('w-soil'), chainStatus: 'CONFIRMED' },
      { deviceCode: 'CAR-A3-01', metric: 'light', windowStart: '2026-08-01 00:00:00', windowEnd: '2026-08-01 06:00:00', minValue: '0.000000', maxValue: '62400.000000', avgValue: '18450.000000', sampleCount: 288, dataHash: hash('w-light'), chainStatus: 'CONFIRMED' },
    ],
    chainVerification: {
      contractAddress: CONTRACT,
      latestTxHash: hash('tx-logi', 64),
      latestChainStatus: 'CONFIRMED',
      itemPayloadHashMatched: true,
      batchPayloadHashMatched: true,
      records: [
        { businessType: 'BATCH_REGISTER', chainStatus: 'CONFIRMED', txHash: hash('tx-batch', 64), payloadHash: hash('pl-batch'), payloadHashMatched: true },
        { businessType: 'ITEM_BIND', chainStatus: 'CONFIRMED', txHash: hash('tx-item', 64), payloadHash: hash('pl-item'), payloadHashMatched: true },
        { businessType: 'TRACE_EVENT_APPEND', chainStatus: 'CONFIRMED', txHash: hash('tx-ev', 64), payloadHash: hash('pl-ev'), payloadHashMatched: true },
      ],
    },
    ai: { available: false, provider: 'disabled' },
  }
}

const products = [
  { id: 1, organizationId: 1, productCode: 'P-WALNUT-001', productName: '临安山核桃', category: '坚果', variety: '薄壳山核桃', originPlace: '浙江省杭州市临安区', status: 'ACTIVE' },
  { id: 2, organizationId: 1, productCode: 'P-TEA-002', productName: '安吉白茶', category: '茶叶', variety: '白叶一号', originPlace: '浙江省湖州市安吉县', status: 'ACTIVE' },
]

const batches = [
  { id: 1, productId: 1, organizationId: 1, batchCode: 'B-20260912-01', productionArea: '临安基地 A3 地块', plantDate: '2026-03-18', harvestDate: '2026-09-12', chainStatus: 'CONFIRMED', status: 'ACTIVE' },
  { id: 2, productId: 2, organizationId: 1, batchCode: 'B-20260410-02', productionArea: '安吉基地 B1 地块', plantDate: '2026-01-05', harvestDate: '2026-04-10', chainStatus: 'CONFIRMED', status: 'CLOSED' },
]

const items = Array.from({ length: 12 }, (_, i) => ({
  id: i + 1,
  batchId: 1,
  traceCodeHash: hash(`item-${i}`),
  traceCodePlain: `B-20260912-01-${String(i + 1).padStart(4, '0')}`,
  traceUrl: `http://127.0.0.1:5173/t/B-20260912-01-${String(i + 1).padStart(4, '0')}`,
  qrStatus: 'GENERATED',
  nfcStatus: i % 3 === 0 ? 'BOUND' : 'UNBOUND',
  chainStatus: i % 7 === 0 ? 'PENDING' : 'CONFIRMED',
  status: 'ACTIVE',
}))

const alerts = [
  { id: 1, farmId: 1, gatewayCode: 'CAR-A3-01', deviceCode: 'CAR-A3-01', metric: 'temperature', alertType: 'THRESHOLD_HIGH', level: 'WARN', value: '46.200000', thresholdMin: '-10.000000', thresholdMax: '45.000000', message: 'temperature 46.2 above max 45 on device CAR-A3-01', occurredAt: '2026-09-28 13:12:00', status: 'OPEN' },
  { id: 2, farmId: 1, gatewayCode: 'GW-01', deviceCode: 'GW-01-SOIL', metric: 'soil_moisture', alertType: 'THRESHOLD_LOW', level: 'WARN', value: '12.400000', thresholdMin: '15.000000', thresholdMax: '85.000000', message: 'soil_moisture 12.4 below min 15 on device GW-01-SOIL', occurredAt: '2026-09-27 05:40:00', status: 'OPEN' },
]

const chainHealth = {
  groupId: 'group0',
  peers: ['127.0.0.1:20200'],
  contractAddress: CONTRACT,
  chainEnabled: true,
  accountAddress: '0xd8d242c3b2c751458471573d5934bd0888a29f48',
  writerStatus: true,
  latestBlock: 48213,
  status: 'OK',
}

const routes = [
  [/^\/api\/public\/trace\/(.+)$/, (m) => (m[1] === 'UNKNOWN' ? null : ok(tracePayload(m[1])))],
  [/^\/api\/chain\/health$/, () => ok(chainHealth)],
  [/^\/api\/products$/, () => ok(products)],
  [/^\/api\/batches$/, () => ok(batches)],
  [/^\/api\/items$/, () => ok(items)],
  [/^\/api\/iot\/alerts/, () => ok(alerts)],
  [/^\/api\/iot\/devices$/, () => ok([])],
  [/^\/api\/iot\/gateways$/, () => ok([])],
  [/^\/api\/iot\/windows/, () => ok([])],
  [/^\/api\/iot\/readings/, () => ok([])],
]

createServer((req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host}`)
  res.setHeader('Content-Type', 'application/json; charset=utf-8')

  for (const [pattern, handler] of routes) {
    const match = url.pathname.match(pattern)
    if (match) {
      const body = handler(match)
      if (body === null) {
        res.statusCode = 400
        res.end(JSON.stringify(fail('400', 'trace code not found')))
        return
      }
      res.statusCode = 200
      res.end(JSON.stringify(body))
      return
    }
  }

  res.statusCode = 404
  res.end(JSON.stringify(fail('404', `no mock route for ${url.pathname}`)))
}).listen(PORT, '127.0.0.1', () => {
  console.log(`[mock] 预览数据服务已启动 http://127.0.0.1:${PORT}`)
  console.log('[mock] 试一下：http://127.0.0.1:5173/t/B-20260912-01-0001')
})
