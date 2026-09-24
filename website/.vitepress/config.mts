import { defineConfig } from 'vitepress'

// GitHub Pages project site: https://samakshkambxj.github.io/OriginSU/
// If the site ever moves to a dedicated <org>.github.io repo, change base to '/'.
const base = '/OriginSU/'

const enSidebar = [
  {
    text: 'Get started',
    items: [
      { text: 'Install', link: '/guide/install' },
      { text: 'Build the kernel', link: '/guide/build-kernel' },
      { text: 'Compatibility & hook modes', link: '/guide/compatibility' },
      { text: 'FAQ', link: '/guide/faq' },
      { text: 'About OriginSU', link: '/guide/about' },
    ],
  },
  {
    text: 'Features',
    items: [
      { text: 'Overview', link: '/features/' },
      { text: 'Origin Veil', link: '/features/veil' },
      { text: 'OriginZygisk', link: '/features/zygisk' },
      { text: 'OriginTune', link: '/features/tune' },
      { text: 'OriginGuard & SuSFS', link: '/features/guard' },
      { text: 'KPM & flashing', link: '/features/kpm-flasher' },
      { text: 'Manager tour', link: '/features/manager' },
    ],
  },
  {
    text: 'Project',
    items: [{ text: 'Roadmap', link: '/roadmap' }],
  },
]

const zhSidebar = [
  {
    text: '入门',
    items: [
      { text: '安装', link: '/zh-Hans/guide/install' },
      { text: '常见问题', link: '/zh-Hans/guide/faq' },
      { text: '关于 OriginSU', link: '/zh-Hans/guide/about' },
    ],
  },
  {
    text: '功能',
    items: [{ text: '总览', link: '/zh-Hans/features/' }],
  },
]

export default defineConfig({
  base,
  lang: 'en-US',
  title: 'OriginSU',
  description: 'A kernel-based root solution for Android — with its own identity.',
  head: [
    ['link', { rel: 'icon', href: `${base}logo.png` }],
    ['link', { rel: 'preconnect', href: 'https://cdn.jsdelivr.net/' }],
    ['link', { rel: 'stylesheet', href: 'https://cdn.jsdelivr.net/npm/jetbrains-mono-webfont@latest/jetbrains-mono.css' }],
    ['link', { rel: 'stylesheet', href: 'https://cdn.jsdelivr.net/npm/misans-vf-4web@latest/dist/result.css' }],
    ['link', { rel: 'stylesheet', href: 'https://cdn.jsdelivr.net/npm/remixicon@latest/fonts/remixicon.css' }],
    ['meta', { name: 'description', content: 'OriginSU — a kernel-based root solution for Android with its own identity. Veil hiding, bundled Zygisk, kernel tuning and installer-grade flashing.' }],
    ['meta', { name: 'keywords', content: 'OriginSU, KernelSU, SukiSU, ReSukiSU, Android, ROOT, GKI, KernelSU modules, Origin Veil, OriginZygisk, OriginTune' }],
    ['meta', { name: 'author', content: 'OriginSU' }],
    ['meta', { property: 'og:title', content: 'OriginSU — kernel-based root for Android' }],
    ['meta', { property: 'og:description', content: 'Hiding built in, not bolted on. Veil cloaking, bundled Zygisk, kernel tuning and installer-grade flashing.' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:image', content: 'https://samakshkambxj.github.io/OriginSU/logo.png' }],
    ['meta', { name: 'twitter:card', content: 'summary' }],
    ['meta', { name: 'theme-color', content: '#ea580c', media: '(prefers-color-scheme: light)' }],
    ['meta', { name: 'theme-color', content: '#fb923c', media: '(prefers-color-scheme: dark)' }],
  ],

  lastUpdated: true,
  cleanUrls: true,

  sitemap: {
    hostname: 'https://samakshkambxj.github.io/OriginSU/',
  },

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: [
          { text: '<i class="ri-book-2-line"></i> Guide', link: '/guide/install' },
          { text: '<i class="ri-apps-2-line"></i> Features', link: '/features/' },
          { text: '<i class="ri-question-line"></i> FAQ', link: '/guide/faq' },
          { text: '<i class="ri-rocket-line"></i> Roadmap', link: '/roadmap' },
          {
            text: 'Links',
            items: [
              { text: 'Releases', link: 'https://github.com/samakshkambxj/OriginSU/releases' },
              { text: 'KernelSU docs', link: 'https://kernelsu.org/' },
              { text: 'Module repository', link: 'https://modules.kernelsu.org/' },
            ],
          },
        ],
        sidebar: enSidebar,
        editLink: {
          pattern: 'https://github.com/samakshkambxj/OriginSU/edit/main/website/:path',
          text: 'Edit this page on GitHub',
        },
      },
    },
    'zh-Hans': {
      label: '简体中文',
      lang: 'zh-Hans',
      link: '/zh-Hans/',
      themeConfig: {
        nav: [
          { text: '<i class="ri-book-2-line"></i> 指南', link: '/zh-Hans/guide/install' },
          { text: '<i class="ri-apps-2-line"></i> 功能', link: '/zh-Hans/features/' },
          { text: '<i class="ri-question-line"></i> 常见问题', link: '/zh-Hans/guide/faq' },
        ],
        sidebar: zhSidebar,
        editLink: {
          pattern: 'https://github.com/samakshkambxj/OriginSU/edit/main/website/:path',
          text: '在 GitHub 上编辑此页',
        },
        outline: { label: '本页目录' },
        docFooter: { prev: '上一页', next: '下一页' },
        lastUpdated: { text: '最后更新' },
      },
    },
  },

  themeConfig: {
    logo: '/logo.png',
    siteTitle: 'OriginSU',
    socialLinks: [{ icon: 'github', link: 'https://github.com/samakshkambxj/OriginSU' }],
    search: { provider: 'local' },
    footer: {
      message: 'Kernel parts GPL-2.0-only · everything else GPL-3.0-or-later.',
      copyright: 'Copyright © 2025-2026 OriginSU',
    },
  },
})
