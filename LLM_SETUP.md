# LLM Setup Guide

This application uses OpenAI's API to generate intelligent, contextual answers from your PDF content.

## Quick Setup

### 1. Get an OpenAI API Key

1. Go to https://platform.openai.com/
2. Sign up or log in
3. Navigate to https://platform.openai.com/api-keys
4. Click "Create new secret key"
5. Copy your API key

### 2. Configure the API Key

**Option A: Environment Variable (Recommended for production)**
```bash
export LLM_API_KEY=sk-your-api-key-here
```

**Option B: Application Properties (Easy for development)**
Edit `backend/src/main/resources/application.properties`:
```properties
llm.api.key=sk-your-api-key-here
```

### 3. Restart the Backend

After setting the API key, restart your Spring Boot application:
```bash
cd backend
mvn spring-boot:run
```

## Configuration Options

Edit `backend/src/main/resources/application.properties` to customize:

```properties
# Your OpenAI API Key
llm.api.key=${LLM_API_KEY:}

# API Endpoint (default OpenAI)
llm.api.url=https://api.openai.com/v1/chat/completions

# Model to use
# Options: gpt-3.5-turbo (cheaper, fast)
#          gpt-4 (better quality, slower, more expensive)
#          gpt-4-turbo (balanced)
llm.model=gpt-3.5-turbo

# Enable/disable LLM
# Set to false to use fallback text extraction
llm.enabled=true
```

## How It Works

1. **Question Asked**: User asks a question about the PDF
2. **Search**: System finds the most relevant chunks from your PDFs
3. **Context Building**: Relevant chunks are combined into context
4. **LLM Processing**: Context and question are sent to OpenAI API
5. **Answer Generation**: LLM generates a natural, contextual answer
6. **Response**: User receives an intelligent answer

## Cost Considerations

- **GPT-3.5-turbo**: ~$0.001 per question (very affordable)
- **GPT-4**: ~$0.03-0.06 per question (higher quality)
- Each question sends context (~500-2000 tokens) + question + response

## Troubleshooting

### "I couldn't generate a proper response"
- Check your API key is correct
- Verify you have credits in your OpenAI account
- Check internet connectivity

### API Rate Limits
- Free tier: 3 requests/minute
- Paid tier: Higher limits based on your plan
- The system includes error handling and will fallback if API fails

### Disable LLM (Use Fallback)
Set in `application.properties`:
```properties
llm.enabled=false
```
This will use simple text extraction instead of LLM generation.

## Alternative LLM Providers

To use a different provider (Anthropic Claude, local models, etc.), modify:
- `backend/src/main/java/com/example/pdfchatbot/service/LlmService.java`

The service uses a standard HTTP POST request, so it can be adapted to any REST API compatible LLM.



