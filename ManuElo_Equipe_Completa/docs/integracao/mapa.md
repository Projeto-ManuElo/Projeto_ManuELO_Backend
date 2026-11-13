# Mapa do projeto (o mapeador preenche e verifica; é a base do contexto de todos)

## Estado inicial informado pelo agente de banco (CONFIRME cada item com evidência; marque CONFIRMADO ou REFUTADO)
- As views criam a lista em memória (ex.: `private final List<...> = DemoData.imoveis()`) em ImoveisView, ContratosView,
  InquilinosView, DocumentosView e outras.
- Os diálogos só alteram a lista da instância da view; ao navegar ou recarregar o Vaadin cria outra view e a lista volta ao DemoData.
- Não existe camada de persistência: nenhum @Entity, JpaRepository, JdbcTemplate ou EntityManager.
- CadastroView só chama UiKit.ir("aprovacao"): não há INSERT em usuarios nem rota ou serviço de criação e consulta.
- SecurityConfig usa anyRequest().permitAll() e nenhuma senha é validada.
- O banco local está de pé (schema e seed via Flyway), mas o app só o toca no boot; nenhuma tela o consulta.

## Arquitetura real
(build tool, módulos, versões de Java/Spring Boot/Vaadin/Hibernate, como as views obtêm dados, onde ficam views,
serviços, entidades, migrations e o DemoData)

## Camadas e caminhos (usado para conferir o território de cada agente)
- views: 
- serviços: 
- repositórios: 
- entidades: 
- migrations: 
- DemoData e UiKit: 

## Rotas reais x Figma
| Rota no código | Rota no Figma | Situação |
|---|---|---|

## Auditoria por tela (demo x real)
| Tela | View | Fonte dos dados hoje | Ações que funcionam | Ações mortas | Itens do backlog |
|---|---|---|---|---|---|
