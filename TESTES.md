# Testes da versão 2.0 no Android 16 / POCO X7 Pro

## Instalação
- [ ] ZIP: arquivos na raiz, `.github/workflows/main.yml` e `.gitignore` presentes.
- [ ] GitHub Actions apresenta execução **verde** e arquivo APK nos Artifacts.
- [ ] APK abre como **Guardião de Dados** com ícone de escudo.
- [ ] O app já existente aceita atualização? Se exibir conflito de assinatura, salve dados importantes antes de desinstalar.

## Consumo
- [ ] Permissão de dados de uso aparece concedida em Configurações > Informações do app.
- [ ] 24h mostra os aplicativos da versão anterior (valores podem variar ao longo do tempo).
- [ ] Pesquisa por `Fotos`, `ChatGPT`, `Meta` e nomes de pacotes retorna os itens esperados.
- [ ] Tocar em um resultado apresenta detalhes; ações `Info do app` e `Marcar bloqueio` funcionam.
- [ ] Períodos de 7 e 30 dias consultam sem travamento.
- [ ] Exportar CSV gera arquivo local compatível com planilha.

## Ao vivo
- [ ] Gráfico recebe amostras a cada cerca de 2 s.
- [ ] Velocidades mudam quando há downloads/upload e podem ficar baixas em repouso.
- [ ] O contador é global, não diz qual aplicativo provocou um pico.
- [ ] Zerar sessão reinicia máximos e eventos.
- [ ] Ajustar limite registra eventos apenas acima do novo limite.
- [ ] Exportação de picos CSV funciona.

## Bloqueio
- [ ] Lista inicial carrega aplicativos comuns e pesquisa.
- [ ] `Perfis de economia` marca seleção sem ativar automaticamente a VPN.
- [ ] `Mostrar apps de sistema` apresenta aviso; serviço essencial requer confirmação específica.
- [ ] Se VPN 1S-VPN estiver ativa, anotar que só uma VPN pode ficar ativa. Não usar ambas simultaneamente.
- [ ] Testar primeiro com **um app comum não essencial**, confirmar a VPN e verificar que ele deixa de acessar a rede.
- [ ] Diagnóstico mostra `Incluído na VPN` para o app, ou motivo de falha; **não** confundir com prova de pacotes individualmente bloqueados.
- [ ] Modificar seleção com VPN ativa apresenta aviso para reiniciar.
- [ ] Desativar VPN restaura a rede do app e permite reativar outra VPN.

## Diagnóstico
- [ ] Android 16 e modelo do POCO aparecem corretamente.
- [ ] Permissão `Dados de uso` é identificada como concedida.
- [ ] Total instalado/selecionado/aplicado atualiza após as alterações.
- [ ] Lista e exportação CSV de auditoria identificam apps incluídos e fora da VPN.

## Limites que NÃO devem ser considerados bugs
- O monitor global **não atribui picos a um app**.
- Alguns UIDs reúnem vários aplicativos.
- `Incluído na VPN` não confirma pacotes descartados daquele aplicativo.
- A lista de apps do sistema pode não cobrir processos compartilhados.
- Fechar o app pode interromper o histórico de picos (sem serviço de gravação permanente).
- A quantidade consumida no relatório local não representa necessariamente a franquia do Wi‑Fi do navio.
