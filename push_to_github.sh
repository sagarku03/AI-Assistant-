#!/bin/bash

# Project JARVIS: 1-Click GitHub Pusher
# Usage: ./push_to_github.sh https://github.com/<your-username>/<your-repo>.git

if [ -z "$1" ]; then
    echo "❌ Error: Please provide your GitHub repository URL."
    echo "Usage: ./push_to_github.sh https://github.com/YOUR_USERNAME/YOUR_REPO.git"
    exit 1
fi

REPO_URL=$1

echo "🚀 Connecting to GitHub: $REPO_URL ..."
git remote remove origin 2>/dev/null
git remote add origin "$REPO_URL"
git branch -M main

echo "📤 Pushing code to GitHub..."
git push -u origin main

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 SUCCESS! Pushed to GitHub successfully!"
    echo "⚡ GitHub Actions automatically APK compile kar raha hai."
    echo "👉 Apne GitHub repo me 'Actions' tab par jayein aur direct 'app-debug.apk' download karke phone me install karein!"
else
    echo "❌ Push failed. Please check your GitHub credentials or repo URL."
fi
