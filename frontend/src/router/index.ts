import { createRouter, createWebHistory } from 'vue-router'
import LiveTranslatePage from '../pages/LiveTranslatePage.vue'
import PlaybackPage from '../pages/PlaybackPage.vue'
import SessionHistoryPage from '../pages/SessionHistoryPage.vue'
import SummaryPage from '../pages/SummaryPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'live', component: LiveTranslatePage },
    { path: '/sessions', name: 'sessions', component: SessionHistoryPage },
    { path: '/summary', name: 'summary', component: SummaryPage },
    { path: '/playback', name: 'playback', component: PlaybackPage }
  ]
})

export default router
