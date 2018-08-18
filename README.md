# QuestionAnswers

This a test project, using GraphQL, Sangria and Neo4j, for posting questions and answers by users. You can checkout the fron-end part of this project [questionasnwers-react-relay](https://github.com/lazymesh/questionasnwers-react-relay)

Schema used:

```
schema {
  query: Query
  mutation: mutation
  subscription: subscription
}

scalar DateTime

type mutation {
  createUser(userId: Int!, name: String!): User!
  createQuestion(text: String!, answer: String!, postedBy: Int!): Question!
}

type Query {
  users: [User!]!
  user(userId: Int!): User
  questions: [Question!]!
  question(text: String!): Question
}

type Question {
  text: String!
  answer: String!
  postedBy: Int!
  createdAt: DateTime!
}

type subscription {
  questionCreated: Question!
  userCreated: User!
}

type User {
  userId: Int!
  name: String!
  questions: [Question!]!
  lastQuestion: Question!
}

```

You can create `users` by using mutation 

```
mutation createuser{
  createUser(id:1, name: "ramesh maharjan"){
    id
    name
  }
}
```

You can create `questions` by using mutation 

```
mutation createQ{
  createQuestion(text:"what is this?", answer: "this is testing", postedBy: 1){
  	text
    answer
    postedBy
  }
}
```

`createuser` and `createQ` are just names given to mutations which can be ignored or excluded

By using above mutations, nodes in `Neo4j` database should be created

Now its time to do queries

You can get the list of all users by using following query 

```
query getusers{
  users{
    id
    name
  }
}
```
You can get a user information by doing 

```
query getuser{
  user(id: 1){
    id
    name
  }
}
```
You can get a user info with latest question he/she posted by doing 

```
query getuserwithlatestQuestion{
  user(id: 1){
    id
    name
    lastQuestion{
      text
      answer
      createdAt
    }
  }
}
```

You can get a user information with all the question and the latest question he/she has posted by doing

```
query getuserwithquestionsandlatestQuestion{
  user(id: 1){
    id
    name
    questions{
      text
      answer
      createdAt
    }
    lastQuestion{
      text
    }
  }
}
```

Just play around !!!
