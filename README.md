# ♻️ ReciclaTech - Sistema de Gestão de Coletas

O **ReciclaTech** é uma plataforma web desenvolvida para modernizar e facilitar a gestão de coletas de materiais recicláveis. O sistema conecta a equipe de campo com a administração, permitindo o controle de preços, checklist de materiais e geração de extratos financeiros.

## 🚀 Tecnologias Utilizadas

O projeto foi desenvolvido utilizando as melhores práticas do mercado, com foco em escalabilidade e performance.

* **Backend:** Java 21, Spring Boot 3
* **Frontend:** Thymeleaf (HTML5), Bootstrap 5 (CSS), JavaScript
* **Banco de Dados:** PostgreSQL (Produção no Neon.tech), H2 (Desenvolvimento)
* **Containerização:** Docker
* **Deploy/Hospedagem:** Render.com
* **Gerenciamento de Dependências:** Maven

## ⚙️ Funcionalidades

### 🏢 Painel Administrativo
* **Gestão de Preços:** Atualização dos valores do kg por tipo de material.
* **Revisão de Coletas:** Aprovação e conferência dos dados enviados pela equipe.
* **Checklist:** Controle de itens e categorias de materiais.

### 🚛 Área da Equipe
* **Registro de Coletas:** Envio de dados sobre materiais coletados.
* **Extratos:** Visualização dos ganhos e histórico de coletas ("Meus Extratos").
* **Segurança:** Login seguro e funcionalidade de Logout.

## 🛠️ Como rodar o projeto localmente

### Pré-requisitos
* Java JDK 17 ou 21 instalado.
* Maven instalado.
* Git instalado.

### Passo a passo

1.  **Clone o repositório:**
    ```bash
    git clone [https://github.com/SEU_USUARIO/reciclatech-backend.git](https://github.com/SEU_USUARIO/reciclatech-backend.git)
    cd reciclatech-backend
    ```

2.  **Configuração do Banco de Dados:**
    O projeto utiliza variáveis de ambiente para segurança. No seu ambiente local (VS Code/IntelliJ), configure as seguintes variáveis ou altere o `application.properties` para usar um banco local:

    * `SPRING_DATASOURCE_URL`
    * `SPRING_DATASOURCE_USERNAME`
    * `SPRING_DATASOURCE_PASSWORD`

3.  **Executar o projeto:**
    ```bash
    mvn spring-boot:run
    ```

4.  **Acessar no navegador:**
    Abra `http://localhost:8080`

## 🐳 Rodando com Docker

Se você tiver o Docker instalado, pode rodar a aplicação sem instalar o Java na máquina:

```bash
# Construir a imagem
docker build -t reciclatech-app .

# Rodar o container
docker run -p 8080:8080 reciclatech-app