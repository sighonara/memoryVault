# PersonalArchive

> A self-hosted content aggregation, bookmark management, and archival platform with AI integration via Model Context Protocol (MCP).

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue.svg)](https://www.typescriptlang.org/)
[![Python](https://img.shields.io/badge/Python-3.11-blue.svg)](https://www.python.org/)

## Overview

PersonalArchive is an enterprise-grade personal content management system that helps you:

- 📚 **Manage bookmarks** across multiple browsers with intelligent deduplication
- 🎥 **Archive YouTube videos** and channels for offline access
- 📰 **Aggregate RSS feeds** and web content
- 🔍 **Search archived content** with full-text search capabilities
- 🤖 **Interact via AI** using Claude through the Model Context Protocol
- 🏗️ **Self-host** on AWS or run locally with Docker

### AI Assistant Integration
If you are an AI assistant (like Claude or Junie) working on this project, please refer to [CLAUDE.md](CLAUDE.md) for project-specific instructions, coding standards, and available tools.

Built with modern enterprise technologies as a showcase of full-stack development capabilities, infrastructure-as-code practices, and AI integration patterns.

## Key Features

### Content Management
- **Cross-browser bookmark synchronization** - Import/export bookmarks from Chrome, Firefox, Safari, and Edge
- **Intelligent URL deduplication** - Automatically detects and merges duplicate bookmarks
- **Content archival** - Saves full HTML, screenshots, and text content of bookmarked pages
- **YouTube video archival** - Download and store videos from your favorite channels
- **RSS feed monitoring** - Track blogs, news sites, and content feeds
- **Hierarchical organization** - Folders, tags, and custom categorization

### Search & Discovery
- **Full-text search** across all archived content
- **Tag-based filtering** and organization
- **Link health monitoring** - Periodic checks for broken links
- **Content recommendations** - Discover related bookmarks and videos

### AI Integration (MCP)
- **Natural language queries** - "Find that Kotlin article I saved last month"
- **Content summarization** - "What were the key points in this video?"
- **Smart recommendations** - "What should I read about GraphQL?"
- **Bookmark management** - Add, tag, and organize via conversation with Claude

### Developer Features
- **GraphQL API** - Flexible, type-safe API for all operations
- **REST endpoints** - Traditional REST API for compatibility
- **Event-driven architecture** - Message queues for async processing
- **Scheduled jobs** - Airflow orchestration for content fetching and maintenance
- **Infrastructure as Code** - Terraform modules for AWS deployment
- **CI/CD pipeline** - GitHub Actions for automated testing and deployment

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Clients                              │
├──────────────┬──────────────────┬─────────────┬─────────────┤
│   Angular    │   React (future) │  Mobile App │   Claude    │
│   Frontend   │                  │   (future)  │   (via MCP) │
└──────┬───────┴────────┬─────────┴──────┬──────┴──────┬──────┘
       │                │                │             │
       │ GraphQL        │ GraphQL        │ REST        │ MCP Protocol
       │                │                │             │
┌──────▼────────────────▼────────────────▼─────────────▼──────┐
│                      API Gateway                             │
└──────┬────────────────┬────────────────┬─────────────┬──────┘
       │                │                │             │
┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐ ┌───▼──────┐
│   Spring    │  │   Python    │  │  Airflow    │ │   MCP    │
│    Boot     │  │  Content    │  │  Scheduler  │ │  Server  │
│  (Kotlin)   │  │  Processor  │  │             │ │(TypeScript)│
│             │  │             │  │             │ │          │
│ - GraphQL   │  │- yt-dlp     │  │- Fetch jobs │ │- Tools   │
│ - REST API  │  │- Scraping   │  │- Health chk │ │- Resources│
│ - Business  │  │- Dedupe     │  │- Cleanup    │ │          │
│   Logic     │  │- Screenshots│  │             │ │          │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘ └───┬──────┘
       │                │                │             │
       └────────┬───────┴────────────────┘             │
                │                                      │
         ┌──────▼──────────┐                          │
         │   PostgreSQL    │◄─────────────────────────┘
         │                 │
         │ - Bookmarks     │
         │ - Videos        │
         │ - Archives      │
         │ - Tags          │
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │   AWS S3        │
         │                 │
         │ - Video files   │
         │ - Screenshots   │
         │ - Archived HTML │
         └─────────────────┘
```

## Technology Stack

### Backend
- **Language**: Kotlin 1.9+
- **Framework**: Spring Boot 3.2
- **API**: GraphQL (Spring for GraphQL), REST
- **Database**: PostgreSQL 15
- **ORM**: Spring Data JPA
- **Message Queue**: RabbitMQ / AWS SQS
- **Testing**: JUnit 5, MockK, TestContainers

### Content Processing Service
- **Language**: Python 3.11+
- **Key Libraries**:
    - `yt-dlp` - YouTube video downloading
    - `BeautifulSoup4` - HTML parsing and web scraping
    - `Playwright` - Headless browser for screenshots
    - `Pillow` - Image processing
- **Testing**: pytest

### MCP Server
- **Language**: TypeScript 5.0+
- **Runtime**: Node.js 20+
- **SDK**: `@modelcontextprotocol/sdk`
- **GraphQL Client**: `graphql-request`
- **Testing**: Jest

### Frontend
- **Framework**: Angular 17+
- **Language**: TypeScript
- **Styling**: TailwindCSS
- **State Management**: NgRx (optional)
- **Testing**: Jasmine, Karma

### Infrastructure & DevOps
- **Containerization**: Docker, Docker Compose
- **Orchestration**: Kubernetes (optional), Docker Swarm
- **Workflow Orchestration**: Apache Airflow
- **IaC**: Terraform
- **CI/CD**: GitHub Actions
- **Cloud**: AWS (EC2, S3, RDS, SQS, CloudWatch)
- **Monitoring**: Prometheus, Grafana (future)

## Prerequisites

### For Local Development
- Docker 24.0+ and Docker Compose 2.20+
- JDK 17+ (for Spring Boot development)
- Node.js 20+ and npm 10+ (for MCP server and frontend)
- Python 3.11+ (for content processor)
- Git

### For AWS Deployment
- AWS Account with appropriate IAM permissions
- Terraform 1.5+
- AWS CLI configured

## Quick Start

### Local Development with Docker Compose

```bash
# Clone the repository
git clone https://github.com/yourusername/personalarchive.git
cd personalarchive

# Start all services
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f
```

Services will be available at:
- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080
- **GraphQL Playground**: http://localhost:8080/graphiql
- **Airflow UI**: http://localhost:8081
- **PostgreSQL**: localhost:5432

### Manual Setup (Development)

#### 1. Backend Service

```bash
cd backend
./gradlew bootRun
```

#### 2. Content Processor

```bash
cd content-processor
pip install -r requirements.txt --break-system-packages
python -m uvicorn main:app --reload
```

#### 3. MCP Server

```bash
cd mcp-server
npm install
npm run build
npm start
```

#### 4. Frontend

```bash
cd frontend
npm install
ng serve
```

#### 5. Airflow

```bash
cd airflow
export AIRFLOW_HOME=$(pwd)
airflow db init
airflow users create --username admin --password admin --firstname Admin --lastname User --role Admin --email admin@example.com
airflow webserver -p 8081 &
airflow scheduler &
```

## Configuration

### Environment Variables

Create a `.env` file in the root directory:

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=personalarchive
DB_USER=postgres
DB_PASSWORD=your_password

# AWS (for production)
AWS_REGION=us-west-2
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_secret
S3_BUCKET_NAME=personalarchive-content

# RabbitMQ / SQS
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# Application
SPRING_PROFILE=dev
FRONTEND_URL=http://localhost:4200
BACKEND_URL=http://localhost:8080

# Content Processing
YOUTUBE_ARCHIVE_PATH=/data/videos
SCREENSHOT_PATH=/data/screenshots
MAX_VIDEO_SIZE_MB=500
```

### MCP Configuration

To use the MCP server with Claude Desktop, add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "personalarchive": {
      "command": "node",
      "args": ["/path/to/personalarchive/mcp-server/dist/index.js"],
      "env": {
        "ARCHIVE_API_URL": "http://localhost:8080/graphql",
        "API_KEY": "your_api_key_if_needed"
      }
    }
  }
}
```

## Usage

### Import Bookmarks

#### Via Web UI
1. Navigate to http://localhost:4200/bookmarks
2. Click "Import Bookmarks"
3. Select your browser's exported HTML file
4. Review detected duplicates
5. Click "Confirm Import"

#### Via API

```bash
curl -X POST http://localhost:8080/api/bookmarks/import \
  -F "file=@bookmarks.html" \
  -F "browser=chrome"
```

#### Via GraphQL

```graphql
mutation ImportBookmarks($file: Upload!, $browser: BrowserType!) {
  importBookmarks(file: $file, browser: $browser) {
    totalImported
    duplicatesSkipped
    errors
  }
}
```

### Archive YouTube Channel

```graphql
mutation AddYoutubeChannel($url: String!) {
  addYoutubeChannel(url: $url) {
    id
    channelName
    videoCount
    status
  }
}
```

### Search Content via MCP (in Claude)

Simply ask Claude:
- "Search my bookmarks for articles about Kotlin coroutines"
- "What videos do I have archived from the SpringDeveloper channel?"
- "Find bookmarks I tagged with 'graphql' from the last month"
- "Add https://example.com to my bookmarks and tag it 'spring-boot'"

### Export Bookmarks

```bash
# Export to HTML (compatible with all browsers)
curl http://localhost:8080/api/bookmarks/export?format=html > bookmarks.html

# Export to JSON
curl http://localhost:8080/api/bookmarks/export?format=json > bookmarks.json
```

## API Documentation

### GraphQL Schema

Full schema available at `/graphiql` endpoint when running locally.

**Key Queries:**
```graphql
# Search bookmarks
bookmarks(query: String, tags: [String], limit: Int): [Bookmark]

# Get archived content
archivedContent(bookmarkId: ID!): ArchivedContent

# Search videos
videos(query: String, channel: String): [Video]

# Get recommendations
recommendations(topic: String, limit: Int): [Bookmark]
```

**Key Mutations:**
```graphql
# Add bookmark
addBookmark(url: String!, title: String, tags: [String]): Bookmark

# Update bookmark tags
updateBookmarkTags(id: ID!, tags: [String]!): Bookmark

# Archive YouTube channel
addYoutubeChannel(url: String!): YoutubeChannel

# Delete bookmark
deleteBookmark(id: ID!): Boolean
```

### REST Endpoints

- `GET /api/bookmarks` - List bookmarks
- `POST /api/bookmarks` - Create bookmark
- `GET /api/bookmarks/{id}` - Get bookmark details
- `PUT /api/bookmarks/{id}` - Update bookmark
- `DELETE /api/bookmarks/{id}` - Delete bookmark
- `POST /api/bookmarks/import` - Import bookmarks file
- `GET /api/bookmarks/export` - Export bookmarks
- `GET /api/videos` - List archived videos
- `POST /api/videos/channel` - Add YouTube channel
- `GET /health` - Health check endpoint

## Deployment

### AWS Deployment with Terraform

```bash
cd terraform

# Initialize Terraform
terraform init

# Review deployment plan
terraform plan

# Deploy infrastructure
terraform apply

# Get outputs (API URL, etc.)
terraform output
```

This will provision:
- EC2 instance (t4g.micro) for application services
- RDS PostgreSQL instance (t4g.micro)
- S3 bucket for content storage
- Security groups and networking
- CloudWatch logs

**Estimated Monthly Cost**: $15-20 USD

### Deploy to Existing EC2 Instance

```bash
# SSH into your EC2 instance
ssh -i your-key.pem ec2-user@your-instance-ip

# Clone repository
git clone https://github.com/yourusername/personalarchive.git
cd personalarchive

# Set environment variables
cp .env.example .env
nano .env  # Edit with your values

# Start services
docker-compose -f docker-compose.prod.yml up -d
```

### Migrate from AWS to Local

```bash
# Export database
docker-compose exec postgres pg_dump -U postgres personalarchive > backup.sql

# Download S3 content
aws s3 sync s3://your-bucket/videos ./data/videos
aws s3 sync s3://your-bucket/screenshots ./data/screenshots

# Run locally with docker-compose
docker-compose up -d

# Import database
docker-compose exec -T postgres psql -U postgres personalarchive < backup.sql
```

## Development

### Project Structure

```
personalarchive/
├── backend/                    # Spring Boot (Kotlin) backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/
│   │   │   │   ├── com/yourname/archive/
│   │   │   │   │   ├── config/        # Spring configuration
│   │   │   │   │   ├── controller/    # REST controllers
│   │   │   │   │   ├── resolver/      # GraphQL resolvers
│   │   │   │   │   ├── service/       # Business logic
│   │   │   │   │   ├── repository/    # Data access
│   │   │   │   │   ├── model/         # Domain models
│   │   │   │   │   └── dto/           # Data transfer objects
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── schema.graphqls
│   │   └── test/
│   ├── build.gradle.kts
│   └── Dockerfile
├── content-processor/          # Python content processing service
│   ├── src/
│   │   ├── downloader/        # YouTube downloader
│   │   ├── scraper/           # Web scraper
│   │   ├── deduplicator/      # URL deduplication
│   │   └── archiver/          # Content archival
│   ├── requirements.txt
│   └── Dockerfile
├── mcp-server/                 # TypeScript MCP server
│   ├── src/
│   │   ├── index.ts           # Main server
│   │   ├── tools/             # MCP tool implementations
│   │   ├── resources/         # MCP resource handlers
│   │   └── graphql/           # GraphQL client
│   ├── package.json
│   ├── tsconfig.json
│   └── Dockerfile
├── frontend/                   # Angular frontend
│   ├── src/
│   │   ├── app/
│   │   │   ├── bookmarks/     # Bookmark management
│   │   │   ├── videos/        # Video archive
│   │   │   ├── search/        # Search interface
│   │   │   └── shared/        # Shared components
│   │   ├── assets/
│   │   └── environments/
│   ├── package.json
│   └── Dockerfile
├── airflow/                    # Airflow DAGs and config
│   ├── dags/
│   │   ├── fetch_youtube.py
│   │   ├── check_links.py
│   │   └── cleanup_old_content.py
│   └── airflow.cfg
├── terraform/                  # Infrastructure as Code
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── modules/
│   │   ├── ec2/
│   │   ├── rds/
│   │   └── s3/
│   └── environments/
│       ├── dev/
│       └── prod/
├── .github/
│   └── workflows/
│       ├── backend-ci.yml
│       ├── frontend-ci.yml
│       └── deploy.yml
├── docker-compose.yml          # Local development
├── docker-compose.prod.yml     # Production deployment
├── .env.example
└── README.md
```

### Running Tests

```bash
# Backend tests
cd backend
./gradlew test

# Content processor tests
cd content-processor
pytest

# MCP server tests
cd mcp-server
npm test

# Frontend tests
cd frontend
ng test

# Integration tests
docker-compose -f docker-compose.test.yml up --abort-on-container-exit
```

### Code Style

- **Kotlin**: ktlint (run `./gradlew ktlintFormat`)
- **Python**: black, flake8, mypy
- **TypeScript**: ESLint, Prettier
- **Pre-commit hooks**: husky for automated formatting

## Roadmap

### Phase 1: Bookmarks ✅
- [x] Basic bookmark management
- [x] Import/export functionality
- [x] URL deduplication
- [x] GraphQL API

### Phase 2: RSS ✅
- [x] Scheduled fetching
- [x] Implement RSS fetcher

### Phase 3: YT-DLP ✅
- [x] yt-dlp
- [x] Stub for S3 storage
- [x] Scheduled fetching

### Phase 4: Cross-Cutting ✅
- [x] Implement logging
- [x] Stub AWS usage
- [x] PostgreSQL full-text search
- [x] Job history tracking
- [x] Structured logging

### Phase 5: Web UI & Auth (Current)
- [x] User authentication & JWT
- [x] Multi-user support (CurrentUser wiring)
- [ ] GraphQL setup & Schema
- [ ] GraphQL resolvers for bookmarks & feeds
- [ ] Angular Frontend implementation
- [ ] Playwright E2E tests

### Phase 6: Infrastructure
- [ ] Terraform
- [ ] Deploy to AWS
- [ ] CI/CD pipeline and Github actions
- [ ] AWS Cognito auth
- [ ] AWS Cloudwatch log retrieval
- [ ] AWS cost tracking

### Phase 7: Real-Time Updates
- [ ] Websocket support for live UI updates

## Contributing

This is a personal learning project, but suggestions and feedback are welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

MIT License - see [LICENSE](LICENSE) file for details

## Acknowledgments

- Inspired by the need for cross-browser bookmark management
- Built with modern enterprise technologies for learning and demonstration
- MCP integration showcases cutting-edge AI integration patterns

## Contact

Your Name - sighonara@gmail.com

Project Link: [https://github.com/yourusername/personalarchive](https://github.com/yourusername/personalarchive)

---

**Note**: This is a portfolio/learning project designed to showcase full-stack development capabilities, modern infrastructure practices, and AI integration. It is not intended for production use without additional security hardening and scale testing.