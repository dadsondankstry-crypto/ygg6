# Checklist de testes — Guardião de Dados 3.0

## Instalação e permissões
- [ ] Actions finalizou sem erro e Artifact contém app-debug.apk.
- [ ] Android instala sem erros de assinatura/versão.
- [ ] Ícone de escudo e nome aparecem.
- [ ] Dados de uso autorizados e Diagnóstico informa a permissão.
- [ ] O app funciona sem internet ativa (histórico local, relatórios, filtros e perfis).

## Consumo e tempo
- [ ] Consumo mostra grupos com tráfego e grupos sem tráfego (UIDs compartilhados sinalizados).
- [ ] Consultar 24h, 7 dias, 30 dias funciona.
- [ ] Escolher início 06:30, final 07:30 → Atualizar → confirmar datas exibidas.
- [ ] Data final anterior à inicial exibe mensagem de erro e não faz consulta.
- [ ] Ordenar maior total, menor, nome, Wi‑Fi, móvel, download, upload e tempo de uso.
- [ ] Buscar Duolingo (ou outro app) e filtrar marcados/livres.
- [ ] Sem permissão, texto explica o que precisa ser ativado.

## Bloqueio
- [ ] Selecionar 2 apps e ativar VPN com consentimento Android.
- [ ] Confirmar que apps selecionados não acessam a internet quando a VPN está ativa.
- [ ] Confirmar que app fora da seleção continua acessando a rede.
- [ ] Desmarcar Duolingo com VPN ativa: confirmar status novo em Diagnóstico; teste de conexão.
- [ ] Marcar Duolingo novamente: confirmar VPN e comportamento.
- [ ] Filtro Liberados, Marcados, Na VPN e Não aplicados mostra lista coerente.
- [ ] Salvar perfil pessoal, substituir seleção, carregar perfil, apagar perfil.
- [ ] Tentar marcar um serviço essencial: aparece confirmação.
- [ ] Não consegue bloquear Guardião de Dados a si próprio.
- [ ] Desativar VPN, restaurar outra VPN se usar.

## Relatórios
- [ ] Em Diagnóstico, lista TODOS os apps, inclusive sem consumo.
- [ ] Abre detalhes, filtro e pesquisa funcionam.
- [ ] `Ver relatório` abre prévia HTML offline sem travar.
- [ ] `HTML bonito`: arquivo abre em navegador; apps, estados e gráficos presentes.
- [ ] `CSV completo`: é arquivo de tabela (não SVG), linhas incluem todos apps, UID, Rx/Tx e estados.
- [ ] `JSON técnico`: JSON válido com `aparelho`, `consulta`, `vpn`, `aplicativos`.
- [ ] Relatórios destacam que dados por UID podem duplicar em linhas de apps compartilhados.
- [ ] Picos globais e exportação de picos continuam funcionando.

## Limitações esperadas
- Tempo 06:30–07:30 não prova precisão por minuto.
- Apps com consumo antes do bloqueio podem continuar com histórico positivo.
- Contagem de descartes da VPN é GLOBAL, não por app.
- O consumo mostrado não é o extrato de cobrança da conexão do navio.
- Ao modificar seleção da VPN ativa pode existir interrupção momentânea.

## Se falhar
Copie a **primeira mensagem real de erro** do GitHub Actions ou registre: marca/modelo, Android, permissão concedida, estado da VPN, passos feitos, resultado esperado/obtido e o JSON técnico (revise privacidade antes de compartilhar).
