           

# Managing Data Access Between Modules   
  
### What Is A Module?   
  
A module is a piece of software that has a functionality by itself. A module can be deployed together with other modules as a monolith or separately as a microservice. When defining a module one should be careful because managing data access between modules becomes harder. Thus it requires a good amount of experience in a specific field to decide. It's better to make the mistake of merging "actual two modules" into one rather than separating a "single module" into two. Because if you separate a module into two when you shouldn't there are going to be lots of data access between these modules, which can be pretty hard to manage especially if there is transactional logic. But sometimes it is necessary to make modules especially when things start to get big. Here is a decision tree I use to decide which kind of strategy I must choose:   
  
### Decision Tree For Read Operations   
  
If there are two services such that A depends on B for a read operation...   
  
- #1 and they are in the same module...  
	- #1.1 and they are deployed in a single application...  
		- [r-1-1-1](#r-1-1-1) and A requires simple data operations over B's data: A should directly use B. 
		- [r-1-1-2](#r-1-1-2) and A requires complex data operations[^cdo] over B's data: A should use B's entity through defining a database relationship.  
	- #1.2 and they are deployed in seperate applications or they are developed by two different teams...  
		- [r-1-2-1](#r-1-2-1) and A requires simple data operations over B's data: B should be put in a common BusinessLogicLayer then A should directly use B itself.  
		- [r-1-2-2](#r-1-2-2) and A requires complex data operations over B's data: A's and B's entities should be put in a common DataAccessLayer then A should use B's entity through defining a database relationship.  
- #2 and they are in different modules... 
	- #2.1 and A requires simple data operations over B's data...  
		- #2.1.1 and they are deployed in a single application...  
			- [r-2-1-1-1](#r-2-1-1-1) and B is owned by a 3rd party and can be changed in the future: A should define BFacadeInterface[^fcd] which by default implemented by using B itself.   
			- [r-2-1-1-2](#r-2-1-1-2) and B is owned by your organization: A should use B through BInterface which is defined by B.  
		- #2.1.2 and they are deployed in seperate applications...   
			- [r-2-1-2-1](#r-2-1-2-1) and B is owned by a 3rd party and can be changed in the future: A should define BFacadeInterface which by default implemented by using BHttpClient to access B's REST endpoints.   
			- [r-2-1-2-2](#r-2-1-2-2) and B is owned by your organization: A should use BHttpClient through BHttpClientInterface.   
	- #2.2 and A requires a complex data operations over B's data...  
		- [r-2-2-1](#r-2-2-1) and consistency is crucial: A's owner module should define a view which then reads from the B's database tables by the access writes given by B.  
		- r-2-2-2 and availability and performance is crucial... 
			-  [r-2-2-2-1](#r-2-2-2-1) and B is owned by a 3rd party and can be changed in the future: A should copy data from B in a different format optimized for its own use case using BEventConsumerClientInterface.   #r2-2-21
			-  [r-2-2-2-2](#r-2-2-2-2) and B is owned by your organization: A should copy data from B in a different format optimized for its own use case using BEventConsumerClient.   
 ---

|Module|Deployment|Complexity|3rd Party|Decision|  
|---|---|---|---|---|  
|same|single|simple|no|[r-1-1-1](#r-1-1-1)|
|same|single|complex|no|[r-1-1-2](#r-1-1-2)|
|same|seperate|simple|no|[r-1-2-1](#r-1-2-1)|
|same|seperate|complex|no|[r-1-2-2](#r-1-2-2)|

|different|single|both|yes|[r-2-1-1-1](#r-2-1-1-1)|
|different|single|both|no|[r-2-1-1-2](#r-2-1-1-2)|
|different|seperate|simple|no|[r-1-2-1](#r-1-2-1)|
|different|seperate|complex|no|[r-1-2-2](#r-1-2-2)|


### r-1-1-1
A should directly use B.

---
### r-1-1-2
A should use B's entity through defining a database relationship.

---
### r-1-2-1
B should be put in a common BusinessLogicLayer then A should directly use B itself.

---
### r-1-2-2
A's and B's entities should be put in a common DataAccessLayer then A should use B's entity through defining a database relationship.  

---

### r-2-2-1
A should define the view not B because:
- It's not possible for B to define views in it's own schema that will fulfill everybodies needs. So A is going to under or overfetch some data.
- That will lead to a case where A wants to integrate with other 3rd party service but it can not know which fields of the view it should fill in order to work correctly.  
 
 
### Decision Tree For Write Operations   
  
If there are two services such that A depends on B for a write operation...   
  
- #1 and they are in the same module...  
	- #1.1 and they are deployed in a single application: A should directly use B which accesses the database directly.  
	- #1.2 and they are deployed in seperate applications or they are developed by two different teams: B should be put in a common BusinessLogicLayer then A should directly use B itself.  
- #2 and they are in different modules...  
	- #2.1 and they are deployed in a single application...  
		- #2.1.1 and B is owned by a 3rd party and can be changed in the future:  A should define BFacadeInterface which by default implemented by using B itself.  
		- #2.1.2 and B is owned by your organization: A should use B through BInterface which is defined by B's owner.  
	- #2.2 and they are deployed in seperate applications...  
		- #2.2.1 and A requires simple data operations over B's data...  
			- #2.2.1.1 and B  is owned by a 3rd party and can be changed in the future: A should define BFacadeInterface which by default implemented by using BHttpClient to access B's REST endpoints.   
			- #2.2.1.2 and B is owned by your organization: A should use BHttpClient through BHttpClientInterface.   
		-  #2.2.2 and A requires long running data operations over B's data:  
			- #2.2.2.1 and B  is owned by a 3rd party and can be changed in the future: A should define BFacadeInterface which by default implemented by using BEventProducerClient.   
			- #2.2.2.2 and B is owned by your organization: A should use BEventProducerClientInterface which by default implemented by using an event/queue bus producer to send  BEvent defined by B's owner.   
  
[^cdo]: complex data operation: batch processing, ordering/filtering after join, db row locking etc.  
  
[^fcd]: purpose of a facade is to abstract a third party service so it is easier to replace. 


  
### Should read and write services be seperated (CQRS)?   
  
After you have identified what method should be used for a services reads and writes if read and write service data comes from different sources or they can come from different sources based on some runtime parameters (reads from db view and writes over httpClient) then you should separate them.  
  
### What is submodule ?  
  
Sub module is a module which is completaly dependent on a parent module but it is developped by a different team. A submodule can use any protected level resource from parent module. But if parent module also depends on the submodule it is an indication of them being a single module in reality. So even is they are developped by different teams they should share resources through DataAccessLayer and BusinessLogicLayer  
  
### When to decompose a module ?  
  
If module A can not be deployed without module B and module B also includes too much code that A will not use. You should decompose module B in to two separate services.  
  
### When to use distributed transaction?  
  
If two services communicate over a network protocol other than database network and consistency is crucial for your application you should use distributed transactions.  

https://www.oreilly.com/radar/modules-vs-microservices/