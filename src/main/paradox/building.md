## Building

This project is built with sbt, with the convenience scripts added:

 * ./coverage.sh -- runs test with code coverage
 * ./makeDoc.sh -- creates and pushes the documents

## Releasing

This project uses the sbt release plugin. If when running
```scala
sbt release
```

You get the error:
```
No tracking branch is set up. Either configure a remote tracking branch, or remove the pushChanges release part.
``` 

You may need to run 
```bash
git push --set-upstream origin master
```
