# Oficina de ferramentas — Super Lab Kellvyn

Este é um **ambiente de desenvolvimento separado do aplicativo**. Não redesenha a interface, não modifica o cérebro/modelo e não cria controles visíveis na Kellvyn IA. A proteção de escopo só permite alterações em `workshop/**` e em `.github/workflows/super-lab.yml`.

## Pipelines

- **Auditoria web:** compila e testa o projeto existente, abre-o num navegador mobile de 444×913 CSS px, guarda capturas da tela principal/configurações, inventaria botões e verifica categorias, menu + e coexistência Chuva+Raios. Salva JSON com dimensões/erros.
- **Android virtual:** compila testes e APK nativo e instala-o num AVD Android 15 com perfil próximo do Poco F6 (1220×2712, 440 dpi, 8 GB de RAM). Captura tela, árvore de acessibilidade, memória e logs. Cada comando ADB tem tempo limite. Para fechar janelas usa Voltar, não coordenadas inventadas.
- **Comparação visual:** utiliza `scripts/compare_poco_f6_captures.py` para crops alinhados sem redimensionar e gera lado a lado, overlay, mapa de diferença e métricas. Se faltarem capturas, relatório marca comparação como ignorada, não aprovada.
- **Estúdio criativo Blender:** gera arquivo de cena editável, preview renderizado, materiais de paleta, chão/sombras e rig de luz Key/Fill/Rim. Os modos 2D e 3D geram cenas; 3D exporta GLB; 4D significa cena 3D animada no tempo (keyframes e frames 1/24/48), não quatro dimensões espaciais.
- **Relatório:** reúne capturas/logs/manifests e monta `index.html` e `report.json` navegáveis. Ferramentas ausentes e testes ignorados aparecem como tais.

## Comandos

```bash
python3 -m unittest discover -s workshop/tests -v
python3 -m py_compile workshop/tools/labctl.py workshop/blender/studio_pipeline.py
node --check workshop/tools/web_audit.mjs
bash -n workshop/tools/android_smoke.sh
python3 workshop/tools/labctl.py doctor --output toolchain.json

blender --background --factory-startup --python workshop/blender/studio_pipeline.py -- --mode 3d --palette emerald --output-dir out/studio/3d
python3 workshop/tools/labctl.py report --input-dir artifacts --output-dir report --status web=success --status android=skipped
```

No GitHub: Actions → **Super Lab — Web, Android virtual, and creative studio**. A execução manual oferece opção para rodar o Android virtual.

## Limites importantes

O AVD é um emulador hospedado no CI, não um Poco F6 físico. O build e screenshots não comprovam inferência offline, temperatura, bateria ou desempenho no aparelho real. O teste não baixa o modelo de IA nem envia conversas a provedores. A cena Blender é um pipeline procedural inicial para expandir depois; não é um editor 3D interativo completo. A interface do produto permanece intocada por este conjunto de ferramentas.
