import type { DeviceExpressionPack, ExpressionPack } from '@/api/modules/expressionPacks'

export function currentExpressionLabel(selection: DeviceExpressionPack | null, pack: ExpressionPack | undefined, dynamicSupported: boolean | undefined) {
  if (!selection) {
    return '未获取'
  }
  if (selection.status === 'READY' || selection.status === 'INSTALLING') {
    return '正在切换，实际形象待设备确认'
  }
  if (selection.status === 'FAILED') {
    return '切换失败，设备沿用原形象'
  }
  if (selection.status === 'ACTIVE' && selection.enabled) {
    if (!pack) {
      return '已启用自定义素材，名称未获取'
    }
    return pack.packType === 'LIFECYCLE_EAF' ? `动态球体 + ${pack.name}` : pack.name
  }
  if (dynamicSupported === undefined) {
    return '默认形象，设备能力未获取'
  }
  return dynamicSupported ? '内置动态球体' : '内置机械眼'
}
