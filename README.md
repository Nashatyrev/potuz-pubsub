# Erasure coding for Pubsub: simulation results comparing Reed-Solomon and RLNC

The writeup is [here](https://github.com/Nashatyrev/potuz-pubsub/blob/main/src/main/notebook/Writeup.ipynb). 

# Build

## Dependencies
Prior to build the following dependencies should be built locally:  
- JLinAlg (`org.jlinalg:jlinalg:0.9.1`)
  ```shell
  git clone https://github.com/JLinAlg/JLinAlg.git
  cd JLinAlg
  git checkout c8fa3b9
  ./gradlew publishToMavenLocal
    ```
- Ideal Pubsub (`net.nashat:ideal-pubsub:1.0`)
  ```shell
  git clone https://github.com/Nashatyrev/ideal-pubsub.git
  cd ideal-pubsub
  git checkout 0467636
  ./gradlew publishToMavenLocal
    ```
## Build 
```shell
cd potuz-pubsub
./gradlew build
```
