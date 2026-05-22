#!/bin/bash

# FinTrack Local Development Startup Script

echo "🚀 Starting FinTrack Application..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17 or higher."
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js 20.19+ or 22.12+."
    exit 1
fi

if ! node -e "const [major, minor] = process.versions.node.split('.').map(Number); process.exit((major === 20 && minor >= 19) || (major === 22 && minor >= 12) || major > 22 ? 0 : 1)"; then
    echo "❌ Node.js $(node --version) is too old. Please install Node.js 20.19+ or 22.12+."
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven 3.6 or higher."
    exit 1
fi

# Check if MySQL is running
if ! command -v mysql &> /dev/null; then
    echo "⚠️  MySQL is not installed. Please install MySQL 8.0 or higher."
    echo "   Or use Docker Compose: docker-compose up --build"
    exit 1
fi

echo "✅ Prerequisites check passed"

# Load local environment variables if present
if [ -f ".env" ]; then
    set -a
    source .env
    set +a
    echo "🔧 Loaded .env"
fi

# Set default environment variables
export JWT_SECRET=${JWT_SECRET:-"fintrack-dev-secret-key-change-in-production"}
export DB_USERNAME=${DB_USERNAME:-"root"}
export DB_PASSWORD=${DB_PASSWORD:-"password"}

echo "🔧 Environment variables set"

# Start backend in background
echo "🔄 Starting backend..."
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local > ../backend.log 2>&1 &
BACKEND_PID=$!
cd ..

# Wait for backend to start
echo "⏳ Waiting for backend to start..."
sleep 10

# Check if backend is running
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "⚠️  Backend might not be ready yet. Check backend.log for details."
fi

# Install frontend dependencies if needed
if [ ! -d "frontend/node_modules" ]; then
    echo "📦 Installing frontend dependencies..."
    cd frontend
    npm install
    cd ..
fi

# Start frontend
echo "🔄 Starting frontend..."
cd frontend
npm start &
FRONTEND_PID=$!
cd ..

echo ""
echo "🎉 FinTrack is starting up!"
echo ""
echo "📱 Frontend: http://localhost:5173"
echo "🔧 Backend API: http://localhost:8080"
echo "📚 API Docs: http://localhost:8080/swagger-ui.html"
echo ""
echo "📋 Process IDs:"
echo "   Backend: $BACKEND_PID"
echo "   Frontend: $FRONTEND_PID"
echo ""
echo "📝 Logs:"
echo "   Backend: tail -f backend.log"
echo "   Frontend: Check the terminal output above"
echo ""
echo "🛑 To stop the application:"
echo "   kill $BACKEND_PID $FRONTEND_PID"
echo "   Or press Ctrl+C in the frontend terminal"
echo ""

# Keep script running
wait $FRONTEND_PID
