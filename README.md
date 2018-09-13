# Play / cats integration 

## Apis 

### List users

``` 
curl -XGET http://localhost:9000/users
```

### Create a user 

``` 
curl -XPOST http://localhost:9000/users -H 'Content-Type: application/json' -d '{"email": "ragnar.lodbrock@gmail.com", "name": "Ragnar Lodbrock"}'
```

### get a user 

``` 
curl -XGET http://localhost:9000/users/ragnar.lodbrock@gmail.com
```


