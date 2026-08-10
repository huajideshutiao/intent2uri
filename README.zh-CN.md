# 流转（Flow）

> [**English**](README.md) | **中文**

**Flow** 是一个轻量的 Android 小工具，把任意应用或链接变成可分享、可搜索的快捷方式。支持：

- 🔗 **URL 重定向** — 通过自定义规则打开任意链接、改写关键字，并重定向到目标应用或网页。
- 🖼️ **搜图** — 分享图片，一键在 7 个图源中反向搜索。
- 🧩 **数字助理集成** — 接入 Android 数字助理底栏（可选 Shizuku）。

## 截图

![1000038109](https://github.com/user-attachments/assets/483f96bf-9be7-489e-9447-adf004f34497)

![1000038110](https://github.com/user-attachments/assets/38e334fa-fe27-4783-94d9-3afa33cad7d8)

![1000038111](https://github.com/user-attachments/assets/f9df6c25-8cf0-4250-8e79-eba1e0fb0981)

## 规则说明

快捷方式规则定义了一个目标（应用 / 文字 / 图片）和一组转换：

- 替换规则**按行分割**。
- 在 `uri`、`extra` 中允许使用 `{key}` 引用替换规则执行后获得的关键字。
- `extra` 键值按行一一对应。

## 使用方式

有三种方式触发规则：

1. **设为默认浏览器** — 打开的 URL 会自动按 URL 匹配规则匹配。
2. **分享到本应用** — 通过系统分享菜单把文字或链接分享到 Flow 处理。
3. **深链引用** — 通过 `kkp://id/key` 直接打开指定条目（前提是该条目没有 URL 匹配规则）。

## 搜图功能

分享图片给 Flow，从 **7** 个支持的图源中选择：Google、百度、SauceNAO、搜图酱、Yandex、ascii2d、AnimeTrace。结果列表会展示每个匹配的相似度与来源，并可直接打开来源链接。

## 系统要求

- Android 7.0+（API 24+）
- Shizuku *（可选，仅高级助理/Shizuku 集成需要）*
