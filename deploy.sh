mvn clean package -P prod #Profilul prod pentru a rula în cloud
docker build -t gcr.io/bookworm-221210/crawler:latest .
gcloud container clusters create crawler-cluster --num-nodes=2
kubectl run crawler --image=gcr.io/bookworm-221210/crawler:latest --port 8080
kubectl expose deployment crawler --type=LoadBalancer --port 80 --target-port 8080
