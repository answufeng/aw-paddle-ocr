# Contributing to aw-paddle-ocr

## 开发习惯

- **语言**：对开源文档与注释建议统一为**简体中文**或**英文**（KDoc 与 `README` 不混用繁简）
- **格式**：提交前在仓库根目录执行 `./gradlew ktlintCheck`；若有差异可 `ktlintFormat`
- **JNI / CMake**：若修改 `src/main/jni`，请在 PR 中说明对 ABI、16KB 与推理行为的影响
- **版本**：遵循 [语义化版本](https://semver.org/)；公共 API 的删除或重命名经 `@Deprecated` 至少保留一版

## Pull Request

- 功能与文档同步更新（`README` 的 API 表须与 [AwPaddleOcr](aw-paddle-ocr/src/main/java/com/answufeng/paddleocr/AwPaddleOcr.kt) 一致）
- 对「一次 `detect` + `OcrResult` 查询」的推荐模式保持清晰，避免在示例中隐式多次全图推理

感谢贡献。
