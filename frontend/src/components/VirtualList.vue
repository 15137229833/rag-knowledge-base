<template>
  <div ref="containerRef" :style="{ height: containerHeight, overflow: 'auto' }">
    <div :style="{ height: totalHeight + 'px', position: 'relative' }">
      <div
        v-for="item in visibleItems"
        :key="item.index"
        :style="{
          position: 'absolute',
          top: item.offset + 'px',
          left: 0,
          right: 0,
          height: itemHeight + 'px'
        }"
      >
        <slot :item="items[item.index]" :index="item.index"></slot>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  items: {
    type: Array,
    required: true
  },
  itemHeight: {
    type: Number,
    default: 60
  },
  containerHeight: {
    type: String,
    default: '400px'
  },
  overscan: {
    type: Number,
    default: 5
  }
})

const containerRef = ref(null)
const scrollTop = ref(0)

// 计算总高度
const totalHeight = computed(() => props.items.length * props.itemHeight)

// 计算可见区域
const visibleItems = computed(() => {
  const start = Math.floor(scrollTop.value / props.itemHeight)
  const visibleCount = Math.ceil(parseInt(props.containerHeight) / props.itemHeight)
  
  const renderStart = Math.max(0, start - props.overscan)
  const renderEnd = Math.min(props.items.length, start + visibleCount + props.overscan)
  
  const items = []
  for (let i = renderStart; i < renderEnd; i++) {
    items.push({
      index: i,
      offset: i * props.itemHeight
    })
  }
  
  return items
})

// 监听滚动事件
const handleScroll = () => {
  if (containerRef.value) {
    scrollTop.value = containerRef.value.scrollTop
  }
}

onMounted(() => {
  if (containerRef.value) {
    containerRef.value.addEventListener('scroll', handleScroll)
  }
})

onUnmounted(() => {
  if (containerRef.value) {
    containerRef.value.removeEventListener('scroll', handleScroll)
  }
})

// 监听 items 变化
watch(() => props.items.length, () => {
  scrollTop.value = 0
})
</script>
