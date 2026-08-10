# Flow

> **English** | [**中文**](README.zh-CN.md)

**Flow** is a lightweight Android utility that turns any app or URL into a shareable, searchable shortcut. It supports:

- 🔗 **URL redirection** — open any URL through your own rules, rewrite keywords, and redirect to the target app or web page.
- 🖼️ **Reverse image search** — share an image and search it across 7 engines in one tap.
- 🧩 **Assistant integration** — plug into the Android digital assistant bar (Shizuku optional).

## Screenshots

![1000038109](https://github.com/user-attachments/assets/483f96bf-9be7-489e-9447-adf004f34497)

![1000038110](https://github.com/user-attachments/assets/38e334fa-fe27-4783-94d9-3afa33cad7d8)

![1000038111](https://github.com/user-attachments/assets/f9df6c25-8cf0-4250-8e79-eba1e0fb0981)

## How rules work

A shortcut rule defines a target (app / text / image) and a set of transformations:

- Replacement rules are entered **line by line**.
- Inside `uri` and `extra`, you can use `{key}` to reference a keyword captured by the replacement rules.
- `extra` key-value pairs map to the lines one-to-one.

## Usage

Flow can be triggered in three ways:

1. **Set as default browser** — URLs you open are automatically matched against your URL match rules.
2. **Share to Flow** — pick Flow from the Android share sheet to process the shared text or link.
3. **Deep link** — open `kkp://id/key` to jump straight to a specific entry (only when that entry has no URL match rule).

## Reverse image search

Share an image to Flow and choose from **7** supported engines: Google, Baidu, SauceNAO, SoutuBot, Yandex, ascii2d, and AnimeTrace. The result list shows similarity and source for each match, and you can open the source link directly.

## Requirements

- Android 7.0+ (API 24+)
- Shizuku *(optional — only needed for advanced assistant/Shizuku integrations)*
