# Guardião de Dados 3.0 — Controle, bloqueio e relatórios offline

**Projeto Android nativo em Java 17**, sem servidor, compilado pelo GitHub Actions. Feito para Android 8+ e pensado para POCO X7 Pro / Android 16 / HyperOS 3. **É o projeto-fonte; o APK é produzido no GitHub**, não está incluído neste ZIP.

## O que há nesta atualização

### 📊 Consumo
- Períodos de 24 horas, 7 dias, 30 dias e **personalizado** por data, hora inicial e final (ex.: 06:30 a 07:30).
- Histórico por UID (grupo de aplicativos) com download, upload, Wi‑Fi, dados móveis e total.
- Ordenar por **maior consumo** (padrão), menor consumo, nome A–Z/Z–A, Wi‑Fi, dados móveis, download, upload, tempo em primeiro plano, selecionados primeiro e liberados primeiro.
- Pesquisar por nome/pacote, filtrar com consumo, selecionados ou fora da seleção, incluir grupos sem consumo.
- Tempo em primeiro plano e última abertura são **estimativas do Android**, NÃO tempo de internet usado.
- A lista e os relatórios identificam **UIDs compartilhados**. Um UID pode representar vários apps: não somar duas vezes o mesmo consumo.

### 🛡️ Bloqueio
- VPN local com tráfego dos apps escolhidos **descartado no próprio aparelho**; não é proxy nem VPN de navegação.
- Buscar um app, marcar para bloquear ou desmarcar para liberar (ex.: Duolingo). Se a VPN já estiver ativa, a atualização solicita a recriação do túnel com a seleção nova. Isso **pode causar uma interrupção breve**.
- Filtros: todos, marcados, liberados, incluídos na VPN e marcados mas ainda não aplicados.
- Contagem de apps reconhecidos, selecionados e incluídos; cada app mostra sua situação e histórico de consumo de seu UID.
- Perfis predefinidos e perfis pessoais: **salvar, carregar e apagar** (armazenados apenas no aparelho).
- Confirmação para serviços essenciais. **O app não permite bloquear a si próprio**.

### 🧾 Diagnóstico e relatórios
- Diagnóstico de permissões, modelo/Android, estado da VPN e erro registrado, quantidade de apps descobertos, grupos UID com consumo e bytes descartados globalmente.
- **Inventário de todos os apps reconhecidos**, inclusive sem tráfego, com pesquisa, filtros e detalhes.
- Um app pode mostrar: **fora da VPN**, **marcado mas não aplicado**, ou **incluído na VPN ativa**. São estados de configuração; **não são prova de conexão ativa ou de bloqueio individual de pacotes**.
- Detalhes de cada app: nome, pacote, UID, se é sistema, consumo histórico, tempo de uso da tela, última abertura estimada e se compartilha UID.
- **Prévia HTML dentro do próprio APK**, sem outro leitor, com visualização organizada, gráfico de barras, total geral único por UID e listagem completa.
- **Exportação HTML** para abrir no navegador, **CSV completo** para planilhas, **JSON técnico** para análise do projeto e correção de problemas. O CSV foi formatado com `;` e BOM UTF-8 para melhor leitura em planilhas. Nunca envie relatórios privados para repositórios públicos.
- Mantidos o gráfico e os picos de tráfego **do dispositivo inteiro** em tempo real, com exportação CSV de picos.

## Limites importantes — não são defeitos

1. **Período por minuto é aproximado.** O Android registra tráfego em blocos. Consultar 06:30–07:00 pode retornar números incompletos ou valores de blocos agregados; não há contagem precisa em tempo real por app sem abordagem mais invasiva.
2. A VPN **não permite saber qual app causou cada pacote descartado**: o contador de descarte atual é agregado. Um app pode apresentar consumo histórico mesmo estando bloqueado agora, por causa de registros anteriores, atrasos e tráfego agregado por UID.
3. A VPN só bloqueia os apps incluídos efetivamente na configuração. Outros apps (inclusive serviços do sistema) podem continuar usando internet. A verificação de `incluído na VPN` não equivale a inspeção do tráfego daquele app.
4. **Somente uma VPN pode estar ativa por vez.** Usar o Guardião pode substituir sua VPN 1S-VPN/semelhante. Evite misturar VPNs; desative a do Guardião para usar outra.
5. Alterar uma seleção enquanto a VPN está ativa **recria o túnel**, podendo deixar um intervalo momentâneo de conectividade. Verifique status depois.
6. Consumo histórico é do Android, não necessariamente igual à franquia informada pelo navio. O monitor de velocidade global não identifica sozinho o responsável pelo pico.
7. Não há conexão a servidores. O APK **não declara permissão INTERNET**. Relatórios gerados são locais e não são enviados automaticamente.
8. Alguns apps de sistema/UIDs podem não ser identificados pelo Android. O aparelho/ROM decide o que expõe. Nenhuma permissão garante acesso irrestrito a processos protegidos.
9. Se a permissão `Dados de uso` estiver negada, será necessário concedê-la em Configurações; em alguns Xiaomi poderá ser necessário habilitar as `configurações restritas` após analisar os riscos.

## Como gerar o APK pelo celular sem mexer nos arquivos

1. Baixe **Guardiao_de_Dados_v3_0_ZIPTOGIT_RAIZ.zip**. **Não extraia nem recompacte.** Todos os arquivos estão na raiz do ZIP, inclusive `.github/workflows/main.yml`.
2. No seu **ZiptoGit / Gerenciador GitHub**, escolha um repositório **vazio** ou a atualização do mesmo projeto e a branch **main**. Para atualizar o mesmo app, use o repositório do Guardião (não o de outro projeto).
3. Se o repositório já tinha arquivos antigos, confira se eles não conflitam. Ao atualizar pelo ZIP, o app que envia pode **adicionar/substituir** arquivos, mas nem sempre apaga os que sumiram no ZIP.
4. Faça **PUBLICAR ZIP NO GITHUB**. Confira no repositório se aparece `.github/workflows/main.yml`.
5. Abra **GitHub → repositório → Actions → Gerar APK - Guardiao de Dados**. O workflow `main.yml` é disparado por `push` em `main` (ou manualmente por `workflow_dispatch`).
6. Aguarde o resultado na página do GitHub. Se ✅, abra a execução, baixe **Guardiao-de-Dados-APK** nos Artifacts e extraia `app-debug.apk`.
7. Instale no celular. Se aparecer erro de assinatura ao atualizar o APK anterior, leia o item abaixo antes de desinstalar.
8. Conceda `Dados de uso` e, se for usar o bloqueio, permissão da VPN. Entre em Diagnóstico para confirmar os dados.

> **Assinatura e atualizações:** o ZIP cria APK **debug** assinado pelo ambiente de compilação. Builds em máquinas diferentes podem ter chaves debug distintas, impedindo atualização direta do app instalado. Desinstalar apaga perfis/seleções locais. Para distribuir e atualizar de modo estável, será necessário configurar uma chave de assinatura própria, protegida nos GitHub Secrets (não dentro do ZIP).

## Como usar os recursos novos

**Período:** Consumo → escolha `Personalizado` → toque nas datas **De** e **Até**, escolha data e horário → toque em `Atualizar`. O resultado é histórico aproximado, não minuto a minuto.

**Liberar Duolingo por um momento:** Bloqueio → pesquisar `Duolingo` → **desmarcar**. Se VPN ativa, o app tenta reconfigurar o túnel automaticamente. Use Duolingo. Para bloquear de novo, **marque** e verifique em Diagnóstico. Não é um temporizador; é manual e reversível.

**Ver liberados:** Bloqueio → filtro `Liberados da seleção` → pesquise ou navegue. O estado da VPN aparece em cada item.

**Salvar perfil:** Bloqueio → `Perfis de economia` → `Salvar seleção como perfil pessoal`. Carregue depois para alternar rotinas (ex.: Trabalho, Estudo).

**Relatório para analisar comigo:** Diagnóstico → `JSON técnico` (mais completo para análises); ou `CSV completo` (planilhas); ou `Ver relatório` / `HTML bonito` (leitura no celular). Abra o HTML com o navegador comum — **não é SVG**. `CSV` = tabela, `SVG` = imagem vetorial; são formatos diferentes.

**Privacidade do relatório:** a exportação inclui nomes dos apps instalados, IDs dos pacotes, hábitos de uso aproximados e configurações do bloqueio. Não publique nem compartilhe sem avaliar os dados pessoais presentes. Quando quiser uma análise, compartilhe o relatório somente com quem confia.

## Estrutura / compilação

- `app/src/main/java/com/guardiao/dados/MainActivity.java`: dados, filtros, períodos, perfis, relatórios e UI.
- `app/src/main/java/com/guardiao/dados/BlockVpnService.java`: VPN local para descarte de pacotes dos apps selecionados.
- `app/src/main/java/com/guardiao/dados/TrafficChartView.java`: gráfico do tráfego **global**.
- `app/src/main/AndroidManifest.xml`: permissões limitadas.
- `.github/workflows/main.yml`: JDK 17, Gradle 8.11.1, Android SDK 35 do runner, `assembleDebug`, artifact APK.
- `TESTES_V3.md` checklist.

**Verificação realizada nesta entrega:** código Java compilado com **stubs locais de APIs Android**, estrutura e ZIP inspecionados. **Não houve compilação APK real nem teste num POCO físico** neste ambiente. A compilação de verdade ocorrerá no GitHub Actions; compartilhe o primeiro erro (se aparecer) para corrigir.
