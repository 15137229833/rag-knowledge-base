import { useBreakpoints } from '@vueuse/core'

/**
 * 响应式断点配置
 */
export const breakpoints = {
  mobile: 768,
  tablet: 1024,
  desktop: 1280
}

/**
 * 响应式检测组合式函数
 */
export function useResponsive() {
  const bp = useBreakpoints(breakpoints)
  
  const isMobile = bp.smaller('mobile')
  const isTablet = bp.between('mobile', 'tablet')
  const isDesktop = bp.greater('tablet')
  
  return {
    isMobile,
    isTablet,
    isDesktop,
    currentBreakpoint: bp.current()
  }
}

/**
 * 移动端优化配置
 */
export const mobileConfig = {
  // 触摸优化
  touchOptimization: true,
  
  // 手势支持
  gestures: ['swipe', 'pinch', 'rotate'],
  
  // 离线缓存
  offlineCache: true,
  
  // 推送通知
  pushNotifications: true,
  
  // 响应式图片
  responsiveImages: true
}
