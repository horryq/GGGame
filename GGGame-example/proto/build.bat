protoc.exe --java_out=../src/main/java example.proto
protoc.exe --plugin=protoc-gen-grpc-java=protoc-gen-grpc-java.exe --grpc-java_out=../src/main/java --proto_path=./ example.proto