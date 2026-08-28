# TO start wsl command
wsl -d Ubuntu-24.04

# to start docker
sudo systemctl start docker

OR 

sudo service docker start

# running process like container 
docker ps

abd@AB-HP-PC:/mnt/c/Users/17abc$ docker ps
CONTAINER ID   IMAGE                   COMMAND                  CREATED        STATUS         PORTS                                             NAMES
af1d066c157c   chromadb/chroma:1.5.9   "dumb-init -- chroma…"   35 hours ago   Up 3 minutes   0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp       infra-chroma-1
3a28f609039d   ollama/ollama:0.32.6    "/bin/ollama serve"      35 hours ago   Up 3 minutes   0.0.0.0:11434->11434/tcp, [::]:11434->11434/tcp   infra-ollama-1

# download model 
docker exec -it <container-name> ollama pull qwen2.5:7b-instruct-q4_K_M
docker exec -it infra-ollama-1 ollama pull nomic-embed-text

docker exec -it <container-name> ollama pull qwen2.5:7b-instruct-q4_K_M
docker exec -it infra-ollama-1 ollama pull qwen2.5:7b-instruct-q4_K_M

# Schema creation if not found
curl.exe -X POST http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections `
  -H "Content-Type: application/json" `
-d '{\"name\": \"fund_reports\"}'

# power shell command 
Invoke-RestMethod -Uri "http://localhost:8080/api/ingest/by-fund-name" -Method POST -ContentType "application/json" -Body '{"fundName":"Vanguard 500 Index Fund"}'


# Check if Ollama is actually still working, independent of your app:

# Ollam response after ingestion
PS C:\Users\17abc> Invoke-RestMethod -Uri "http://localhost:8080/api/ingest/by-fund-name" -Method POST -ContentType "application/json" -Body '{"fundName":"Vanguard 500 Index Fund"}'

fundName                chunksIndexed sourceUrl
--------                ------------- ---------
Vanguard 500 Index Fund           145 https://www.sec.gov/Archives/edgar/data/914036/000119312524060519/d737576dex99...

