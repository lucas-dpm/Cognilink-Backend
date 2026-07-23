# 📱 CogniLink Backend

Uma API robusta e inteligente desenvolvida em Ktor para potencializar o aprendizado via Inteligência Artificial Generativa.

---

# 📖 Sobre o Projeto

O **CogniLink Backend** é o motor de inteligência por trás do ecossistema CogniLink. Ele foi projetado para resolver o desafio da retenção de conhecimento e compreensão profunda de conteúdos complexos.

- **Contexto:** Em um mundo saturado de informações, estudantes e profissionais precisam de ferramentas que validem seu entendimento real, não apenas a memorização.
- **Problema que resolve:** A passividade no estudo. O projeto transforma a leitura passiva em um processo ativo de diálogo e teste.
- **Importância:** Facilita a aplicação da **Técnica de Feynman** e a criação automatizada de materiais de revisão (Flashcards) baseados em documentos reais.
- **Motivação:** Explorar as capacidades dos modelos de linguagem de larga escala (LLMs) para criar um tutor educacional personalizado.
- **Objetivos principais:** Fornecer endpoints seguros e performáticos para análise de documentos, geração de flashcards e sessões interativas de chat.

---

# ✨ Funcionalidades

O sistema oferece um conjunto de ferramentas focadas em aprendizado ativo:

- **Análise Inteligente de Documentos:** Processamento de arquivos PDF e PPTX para extração automática de temas centrais e tópicos de estudo.
- **Geração Automática de Flashcards:** Criação de cards de estudo (Básico, Múltipla Escolha, Verdadeiro/Falso) com dicas progressivas e suporte multilíngue.
- **Simulador da Técnica de Feynman:** Chat interativo onde o usuário explica um conceito para uma "persona" leiga (ex: criança de 5 anos ou avó gentil), validando a clareza do seu entendimento.
- **Avaliação Pedagógica:** Comparação semântica de respostas de usuários com gabaritos, fornecendo feedback mnemônico e encorajador.
- **Gestão de Sessões:** Controle de estado de chats interativos utilizando cache de alta desempenho.

---

# 🏛 Arquitetura

O projeto segue uma arquitetura modular baseada em **Features** e **Plugins**, característica do framework Ktor, garantindo escalabilidade e facilidade de manutenção.

- **Arquitetura:** Modularizada por domínio/funcionalidade.
- **Motivos da escolha:** O Ktor permite uma estrutura leve onde cada funcionalidade (AI, Firebase) é independente, facilitando testes e substituição de serviços externos.
- **Fluxo de Dados:** 
    1. A requisição chega via HTTP.
    2. O `Routing` direciona para a feature específica.
    3. O `Service` processa a lógica de negócio (chama LLM, consulta cache).
    4. O `Response` é serializado em JSON e retornado ao cliente.

### Estrutura de Pastas

```text
src/main/kotlin/com/lucasdpm/cognilink/
├── features/               # Funcionalidades principais do domínio
│   ├── ai/                 # Lógica de integração com LLMs
│   │   ├── clients/        # Clientes HTTP para APIs externas (Gemini)
│   │   ├── models/         # DTOs e Modelos de dados da funcionalidade
│   │   └── AiService.kt    # Orquestração da inteligência artificial
│   └── firebase/           # Integração com serviços Google Firebase
├── plugins/                # Configurações de infraestrutura (Ktor Plugins)
│   ├── Routing.kt          # Definição das rotas da API
│   ├── Serialization.kt    # Configuração de JSON (Kotlinx)
│   ├── RedisConfig.kt      # Configuração do banco de dados em cache
│   └── StatusPages.kt      # Tratamento global de erros/exceções
└── Application.kt          # Ponto de entrada do servidor
```

---

# 🛠 Tecnologias Utilizadas

| Tecnologia | Uso |
|------------|-----|
| **Kotlin** | Linguagem principal de desenvolvimento (JVM) |
| **Ktor** | Framework para criação de microserviços assíncronos |
| **Google Gemini AI** | Modelo de linguagem para análise e geração de conteúdo |
| **Redis** | Armazenamento em cache para sessões do Chat Feynman |
| **Kotlinx Serialization** | Serialização e desserialização de JSON |
| **Jedis** | Cliente Java para comunicação com Redis |
| **Logback** | Sistema de logging para monitoramento |
| **Docker** | Containerização da infraestrutura de suporte |

---

# 📦 Bibliotecas

- **io.ktor:ktor-server-core:** núcleo do servidor Ktor.
- **io.ktor:ktor-client-cio:** cliente HTTP assíncrono para chamadas à API do Gemini.
- **org.jetbrains.kotlinx:kotlinx-serialization-json:** manipulação eficiente de dados JSON.
- **redis.clients:jedis:** manipulação de persistência temporária no Redis.
- **io.ktor:ktor-server-status-pages:** Gerenciamento elegante de erros HTTP.

---

# 🗄 Banco de Dados

O projeto utiliza o **Redis** como camada de persistência volátil.

- **Modelo:** Chave-Valor.
- **Finalidade:** Armazenar o histórico de mensagens e o estado das sessões da Técnica de Feynman.
- **Estratégia:** As sessões possuem um tempo de expiração (TTL) de 30 minutos, garantindo que a memória do servidor seja preservada e os chats não permaneçam abertos indefinidamente.

---

# 🌐 Comunicação com APIs

A principal integração externa é com a **Google Generative AI API (Gemini)**.

- **Comunicação:** Realizada via cliente `CIO` (Ktor Client) com suporte a corrotinas.
- **Tratamento de Erros:** Implementação de lógica de **Retry** com backoff exponencial para lidar com limites de taxa (Rate Limit 429) e instabilidades temporárias.
- **Serialização:** Mapeamento rigoroso dos objetos de requisição e resposta do Gemini para garantir integridade dos dados.
- **Segurança:** As chaves de API são gerenciadas via variáveis de ambiente/arquivos de propriedades locais.

---

# ⚙ Requisitos

- **Java/JDK:** 21+
- **Kotlin:** 2.3.21
- **Gradle:** 8.x
- **Docker:** Necessário para rodar o Redis localmente.

---

# 🚀 Como executar

Siga os passos abaixo para rodar o backend em sua máquina local:

1. **Clonar o Repositório:**
   ```bash
   git clone https://github.com/lucasdpm/cognilink-backend.git
   cd cognilink-backend
   ```

2. **Subir Infraestrutura (Redis):**
   ```bash
   docker-compose up -d
   ```

3. **Configurar Variáveis:**
   Crie um arquivo `local.properties` na raiz do projeto e adicione suas configurações (consulte [local.properties.example](local.properties.example)):
   ```properties
   GEMINI_API_KEY=sua_chave_aqui
   
   # Configurações do Redis
   REDIS_HOST=localhost
   REDIS_PORT=6379
   # REDIS_PASSWORD= (opcional)
   ```

4. **Executar o Servidor:**
   ```bash
   ./gradlew run
   ```
   O servidor estará disponível em `http://localhost:8080`.

---

# 🔧 Configuração

### Arquivos Importantes:
- `local.properties`: Armazena a `GEMINI_API_KEY`.
- `application.yaml`: Configurações de porta e plugins do Ktor.
- `docker-compose.yml`: Define o serviço Redis.

---

# 🧪 Testes

A estratégia de testes foca na confiabilidade da lógica de IA e rotas:

- **Testes Unitários:** Validação dos serviços de IA e manipulação de DTOs.
- **Testes de Integração:** Utiliza o `testApplication` do Ktor para simular chamadas reais aos endpoints.
- **Execução:**
  ```bash
  ./gradlew test
  ```

---

# 📈 Melhorias Futuras

- [ ] Implementação de autenticação via JWT/Firebase Auth.
- [ ] Suporte a mais formatos de documentos (Excel, Imagens).
- [ ] Integração com outros modelos LLM (GPT-4, Claude) como fallback.
- [ ] Dashboard de monitoramento de performance de IA.

---

# 🤝 Como contribuir

Contribuições são muito bem-vindas!
1. Faça um **Fork** do projeto.
2. Crie uma **Branch** para sua feature (`git checkout -b feature/NovaFeature`).
3. Dê um **Commit** nas suas mudanças (`git commit -m 'Add: Nova funcionalidade'`).
4. Dê um **Push** na Branch (`git push origin feature/NovaFeature`).
5. Abra um **Pull Request**.

---

# 👨‍💻 Desenvolvedores

- **Lucas DPM** - Lead Developer - [LinkedIn](https://www.linkedin.com/in/LucasPMartins) | [GitHub](https://github.com/lucas-dpm)

---

# 📄 Licença

Este projeto está sob a licença **MIT**. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

# 🙏 Agradecimentos

- À **UFU** pelo apoio e infraestrutura de pesquisa.
- Aos professores e orientadores do projeto de **Prof. Dr. Alexsandro Santos Soares** e **Prof. Dr. Rafael Dias Araújo**.
- À comunidade Open Source pelas bibliotecas e ferramentas incríveis.

---
*Ano: 2026*