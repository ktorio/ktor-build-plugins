To test `runDocker` command, do the following:

1. Run `runDocker` command
    - `runDocker` will fail to build the project in Docker image, but it will also create Dockerfile.
      The reason for the fail is that it cannot find Ktor plugin distribution.
2. Run `buildFatJar` command
    - This will build the fat jar for this project
3. Comment out `RUN gradle buildFatJar --no-daemon` command
    - This will exclude building the project inside Docker
4. Run `runDocker` command again, it should work then
    - This will create the Docker image with source code AND build output
      which will be used to run the application inside the container.