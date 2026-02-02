# Troubleshooting Guide

## Common Errors and Solutions

### Backend Errors

#### 1. Port 8080 Already in Use
**Error**: `Port 8080 is already in use`

**Solution**:
- Stop any other application using port 8080
- Or change the port in `backend/src/main/resources/application.properties`:
  ```
  server.port=8081
  ```
- Update frontend to use the new port in `frontend/app/page.tsx`

#### 2. PDF Directory Not Found
**Error**: `Warning: PDF directory not found`

**Solution**:
- Make sure the `pdfs` folder exists at the project root
- Check the console output for "PDF directory found at: ..."
- Ensure PDF files have `.pdf` extension (lowercase)

#### 3. PDF Processing Errors
**Error**: `Error reading PDF: ...`

**Solution**:
- Check if the PDF file is corrupted
- Ensure PDFs are not password-protected
- Verify file permissions allow reading

#### 4. No Text Extracted from PDF
**Error**: `Warning: No text extracted from ...`

**Solution**:
- The PDF might be image-based (scanned) - need OCR
- Try a different PDF with actual text content
- Check if PDF is corrupted

### Frontend Errors

#### 1. Cannot Connect to Backend
**Error**: `Failed to fetch` or network errors

**Solution**:
- Verify backend is running on port 8080
- Check CORS settings (should be `@CrossOrigin(origins = "*")`)
- Ensure backend URL in frontend matches: `http://localhost:8080`

#### 2. React/Next.js Errors
**Error**: Various React/TypeScript errors

**Solution**:
- Run `npm install` in the frontend directory
- Check Node.js version (should be 16+)
- Clear `.next` cache: `rm -rf .next` then restart

## Debugging Steps

1. **Check Backend Logs**:
   ```
   cd backend
   mvn spring-boot:run
   ```
   Look for:
   - "PDF directory found at: ..."
   - "Found X PDF file(s) in directory"
   - "Loaded X text chunks from PDFs"

2. **Check Frontend Console**:
   - Open browser DevTools (F12)
   - Check Console tab for errors
   - Check Network tab for API call failures

3. **Test Backend Directly**:
   ```bash
   curl http://localhost:8080/api/status
   ```

4. **Verify PDF Files**:
   ```bash
   ls -la pdfs/
   # Should show .pdf files
   ```

## Quick Fixes

### Restart Everything
```bash
# Terminal 1 - Backend
cd backend
mvn clean spring-boot:run

# Terminal 2 - Frontend  
cd frontend
npm run dev
```

### Clear Cache
```bash
# Backend
cd backend
mvn clean

# Frontend
cd frontend
rm -rf .next node_modules
npm install
```



