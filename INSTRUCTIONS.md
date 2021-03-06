# Homework 3 and Course Project

### Description: you will gain experience with the Actor-based computational model, implementation and fine-tuning overlay network algorithms and simulating them in AWS cloud datacenter.

### Grade: 10% for homework and 20% for the course project

#### You can obtain this Git repo using the command ```git clone git@bitbucket.org:cs441-fall2020/overlaynetworksimulator.git```. You cannot push your code into this  repo, otherwise, your grade for this homework will be ZERO! You can push your code into your forked repo.

## Preliminaries
If you have not already done so as part of your previous course homeworks, you must create your account at [BitBucket](https://bitbucket.org/), a Git repo management system. It is imperative that you use your UIC email account that has the extension @uic.edu. Please refer to as homeworks 1 and 2 for the preliminaries, so that they do not have to be repeated here.

Please set up your account with [Dockerhub](https://hub.docker.com/) so that you can push your container with the project and your graders can receive it by pulling it from the dockerhub.

## Overview
In this course project, you will loop back to your first homework but at a different level. You will solidify the knowledge of resilient overlay networks by designing and implementing a simulator of a cloud computing facility, specifically a reliable overlay network using the Chord and [Content Addresseable Network (CAN)](https://people.eecs.berkeley.edu/~sylvia/papers/cans.pdf) algorithms for distribution of work in a cloud datacenter. Your goal is to gain experience with the fundamentals of _Distributed Hash Tables (DHTs)_ and you will experiment with resource provisioning in the cloud environment. You will implement a cloud simulator in Scala using [Akka actors](https://akka.io/) and you will build and run your project using the _SBT_ with the _runMain_ command from the command line. In your cloud simulator, you will create the following entities and define interactions among them: actors that simulate users who enter and retrieve data from the cloud, actors who represent servers (i.e., nodes) in the cloud that store the data, and case classes that represent data that are sent to and retrieved from the cloud. The entry point to your simulated cloud will be defined with a RESTful service using [Akka/HTTP](https://doc.akka.io/docs/akka-http/current/introduction.html). 

## WARNING
There are a few implementations of cloud simulators and Chord implementations on the Internet. I know about (almost) all of them. You can study these implementations and feel free to use the ideas in your own implementation, and you must acknowledge what you use in your README. However, blindly copying large parts of some existing implementation in your code will result in receiving the grade F for the entire course with the transfer of your case of plagiarism to the Dean of Students Office, which will be followed with severe penalties. Most likely, you will be suspended or complete dismissed from the program in the worst case. Please do not plagiarize existing implementations, it is not worth it!

## Learning Akka
Actor models are widely used in high-performant cloud-based applications that are composed of distributed objects. Proposed in [the seminal paper in 1973](https://www.ijcai.org/Proceedings/73/Papers/027B.pdf) the Actor model is implemented for the platform Akka. 

* Once you learn how to implement the basic Akka actor applications, your goal is to learn Akka Cluster and Akka Cluster Sharding models because you will use the latter to build your overlay network simulator to run in multiple address spaces (e.g., VMs or EC2 instances). 

* Also, you will learn to use Lightbend telemetry to monitor the behavior of your application where the actors are distributed across multiple disjoint address spaces. It is estimated that you will invest an equivalent of 15 hours of your time to go through the training phase assuming that you know little to nothing about the Actor model when you start.

## Functionality

**The input to your cloud simulator is**

1. The number of users

2. The number of the computers in the cloud 
	- Depending on your RAM and CPU it may be in millions

3. The minimum and the maximum number of requests per minute that each user actor can send

4. The duration of the simulation in minutes (more than one and less than 1000)

5. The time marks (e.g., every three minutes during 20min simulation) when the system will capture a global state for testing

6. The list of the items in a file (e.g., list of movies that include the title, the year, and the revenue)

7. The ratio of read/write requests and the minimum and maximum percentages of the computing nodes that can fail within a pre-configured period of time
	- A read request will retrive an item from the cloud (e.g., a movie using its title/year)
	- A write RPC request will store an item in the cloud (e.g., uploading a movie using its title/year 
		- The GBs of data that contain the actual movie content will not be uploaded)

> For additional 3% bonus for your homework, you can integrate your Scala simulator with a [statistical package called R](https://www.r-project.org/) to use its functions to implement the [accept/reject method](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2924739/) for sampling probabilistic distributions, which you can use to model various aspects in your simulator	
> 
* ex) the arrival of data items to store and their sizes or the failures of servers. 
* As a result, your simulator is guided by probabilistic distributions with certain predefined parameters (e.g., the mean and the variance for the normal distribution) that you choose

* Use a random generator to generate the number of requests for each actor that represents RPC clients using some probabilistic distribution of your choice
	* You can implement different distributions or select ones from the R package or some other open-source libraries
* Once created, actors that represent RPC clients will generate and send data to the cloud endpoint(s), which will then use the algorithm CAN to deliver this data to the actors that simulate cloud servers to store or to retrieve the data
* You will use a logging framework to log all requests sent from actors and received by the cloud and responses that are returned by the cloud actors. The log will serve as the output verification of the functionality of your cloud simulator

***Your assignment can be divided roughly into five steps***

1. Learn how actor-based programming systems work and what your building blocks are in Akka and Akka/HTTP. [Akka documentation is comprehensive with many examples](https://doc.akka.io/docs/akka/current/)
	- I suggest that you load the source code of Akka examples into IntelliJ and explore its classes, interfaces, and dependencies

2. You design your own cloud simulator for the Chord system for homework 3

3. You will create an extended implementation of the simulator with the algorithm CAN. 

4. You will run multiple simulations with different parameters, statistically analyze the results and report them in your documentation with explanations why some variations of error recovery result in more stability than the others in your simulations. 

5. You will create a docker configuration and build a dockerized container using your cloud simulator, and you will upload it to the docker hub using your account

### Functionality For Homework 3
Implement the Chord algorithm using the convergent hashing that we studied in class, which is a realization of a DHT protocol

[Chord: A Scalable Peer-to-peer Lookup Service for Internet Applications](https://pdos.csail.mit.edu/papers/chord:sigcomm01/chord_sigcomm.pdf)

* Chord will store key-value pairs and find the value associated with a key that is submitted by an actor, which simulates a user

* Chord distributes actors that simulate cloud servers over a dynamic network of virtual nodes and it implements a protocol for finding these objects once they have been placed in the overlay network 
	* You can assume one computer per node

* There is an invisible network that connects cloud servers, which are simulated by the actors
	* These actors impose their own overlay network by using Chord to send messages directly to one another
	* Every node in this network is simulated as an actor for looking up keys for user actors and for determining which actors will serve as key stores

> For the homework you do not have to deal with nodes failures and you do not need to model node joins and leaves as part of failures. This part is left for the course project.

##### Chord for HW3

Every key inserted into the DHT must be hashed, so that Chord will determine a node designated by the hashed value of the key, which is an m-bit unsigned integer. According to Chord, the the range of hash values for the DHT contains between 0 and (2 power m-1) inclusive. You can use a 128-bit (or a higher bit content) hash values produced by message digest algorithms such as MD5 or SHA-1 or some other hashing algorithms. You can make it a plugin feature in your simulator. An example of using MD5 in Scala is the following:
```java
import java.security.MessageDigest
def md5(s: String) = { MessageDigest.getInstance("MD5").digest(s.getBytes) }
val hashValue = md5("CS441_courseproject")
```

Actors that simulate nodes in the simulated cloud have the corresponding hash values that can be generated using unique names that your will assign to these nodes and they will be ordered based on those hashes (e.g., Node_123 => 0xDEADBEEF). Recall from the paper and the lecture that Chord orders all nodes in a ring, in which each node's successor is the node with the next highest hash value. To complete the circle, the node with the largest hash value has the node with the smallest hash value as its successor. Of course, if an item does not exist in the cloud, a corresponding "not found" message will be returned to the user actors in response to their get requests.

To look up a key, a request is sent around the ring, so that each node (after determining that it does not hold the value itself) determines whether its successor is the owner of the key, and forwards the request to this successor. 

As part of Chord, the node asks its successor to find the successor of the key interatively, repeating the search procedure until the node is found or the error message is produced. This is done using the finger table at each node. Details are discussed in the paper and in my lecture. To recapitulate briefly, the number of entries in the finger table is equal to m, where m is defined above. Entry e in the finger table, where 0 <= e < m, is the node which the owner of the table believes is the successor for the (hash value + 2 power e). When some node actor N receives a request to find the successor of the key defined by its hash value, it first determines whether N or N's successor is the owner of the hash value, and if so, then N services the request or forwards it to the successor. Otherwise, N locates a node in its finger table such that this node has the largest hash smaller than the hash value, and forwards the request to this node actor. You can implement variations of this algorithm and describe it in your README.

##### Simulation for HW3
* As part of testing, you must capture the global state of the system in the YAML format and dump it
	* The time during which the dump occurs is defined as the input to the simulator program (likely in the config file)
* The simulator has the power to freeze the system and walk over all actors to obtain their local states and combine them into the global state 
	* Global state is saved file whose location is defined as part of the input (likely in the config file)
	* After dumping the state into the file, the simulator resumes the process.

### Functionality For Course Project

#### New Additions
* Add the implementation of the algorithm CAN to your project
* Introduce random failures of the computing nodes and network partition events that lead to the increase of the property spread.
* Introduce the replication mechanisms whereas the stored items are replicated on neighbor nodes based on predefined time threshold and the available free space on the designated nodes
* In your simulation nodes will leave and join randomly leading to rebalancing of the items assigned to them and their neighbors
* Experiment with different rates of node joining and leaving to determine at what point the cloud overlay network will lose the balance

* Compare the results of the simulations using the algorithms Chord and CAN and make conclusions about their behaviors

## Baseline Submission for Homework 3
Your baseline project submission should include your implementation of the Monte Carlo simulation of the algorithm Chord without fault tolerance, a conceptual explanation in the document or in the comments in the source code of how your iterative algoritnm works to solve the problem, and the documentation that describe the build and runtime process, to be considered for grading. Your project submission should include all your source code written in Scala as well as non-code artifacts (e.g., configuration files), your project should be buildable using the SBT, and your documentation must specify how you paritioned the data and what input/outputs are. Simply copying Java/Scala programs from examples at HGithub or other public open source repos and modifying them a bit will result in rejecting your submission.

## Baseline Submission for Course Project
Your baseline project submission should include your implementation of the Monte Carlo simulation for Chord and CAN with fault tolerance, a conceptual explanation in the document or in the comments in the source code of how your iterative algoritnm works to solve the problem, and the documentation that describe the build and runtime process, to be considered for grading. Your project submission should include all your source code written in Scala as well as non-code artifacts (e.g., configuration files), your project should be buildable using the SBT, and your documentation must specify how you paritioned the data and what input/outputs are. Simply copying Java/Scala programs from examples at HGithub or other public open source repos and modifying them a bit will result in rejecting your submission. As part of your experimentation you will use your AWS credit or your personal developer subscription to deploy your simulator in AWS EC2 instances and make a short movie describing all steps of your deployment and experimentation.

## Piazza collaboration
You can post questions and replies, statements, comments, discussion, etc. on Piazza. For this homework, feel free to share your ideas, mistakes, code fragments, commands from scripts, and some of your technical solutions with the rest of the class, and you can ask and advise others using Piazza on where resources and sample programs can be found on the internet, how to resolve dependencies and configuration issues. When posting question and answers on Piazza, please select the appropriate folder, i.e., hw3 to ensure that all discussion threads can be easily located. Active participants and problem solvers will receive bonuses from the big brother :-) who is watching your exchanges on Piazza (i.e., your class instructor). However, *you must not post your source code!*

## Git logistics
**This is a group project,** with at least one and at most five members allowed in a group. Each student can participate in at most one group; enrolling in more than one group will result in the grade zero. Each group will select a group leader who will create a private fork and will invite the other group classmates with the write access to that fork repo. Each submission will include the names of all groupmates in the README.md and all groupmates will receive the same grade for this course project submission. Group leaders with successful submissions and good quality work will receive an additional 2% bonus for their management skills if the group submits a good quality project and if teammates complete it without complaints and airing grievances about each other.

Making your fork public, pushing your code into the main repo, or inviting other students besides your group members to join your fork for an individual homework will result in losing your grade. For grading, only the latest push timed before the deadline will be considered. **If you push after the deadline, your grade for the homework will be zero**. For more information about using the Git and Bitbucket specifically, please use this [link as the starting point](https://confluence.atlassian.com/bitbucket/bitbucket-cloud-documentation-home-221448814.html). For those of you who struggle with the Git, I recommend a book by Ryan Hodson on Ry's Git Tutorial. The other book called Pro Git is written by Scott Chacon and Ben Straub and published by Apress and it is [freely available](https://git-scm.com/book/en/v2/). There are multiple videos on youtube that go into details of the Git organization and use.

Please follow this naming convention while submitting your work : "Group_NUMBER" without quotes, where NUMBER specifies your assigned gGroup numbern. I repeat, make sure that you will give both your TA and the course instructor the read access to your *private forked repository*.

## Discussions and submission
You can post questions and replies, statements, comments, discussion, etc. on Piazza. Remember that you cannot share your code and your solutions privately, but you can ask and advise others using Piazza and StackOverflow or some other developer networks where resources and sample programs can be found on the Internet, how to resolve dependencies and configuration issues. Yet, your implementation should be your own and you cannot share it. Alternatively, you cannot copy and paste someone else's implementation and put your name on it. Your submissions will be checked for plagiarism. **Copying code from your classmates or from some sites on the Internet will result in severe academic penalties up to the termination of your enrollment in the University**. When posting question and answers on Piazza, please select the appropriate folder, i.e., hw1 to ensure that all discussion threads can be easily located.


## Submission deadline and logistics
You should form your group and you can use Piazza to find your group mates by October 23. Your eventual submissions will include the code for the simulator, your documentation with instructions and detailed explanations on how to assemble and deploy your simulator both in IntelliJ and CLI SBT, and a document that explains how you built and deployed your simulator and what your experiences are, and the results of the simulation and their **in-depth analysis**. Again, do not forget, please make sure that you will give both your TA and your instructor the read access to your private forked repository. The names of all group members should be shown in your README.md file and other documents. Your code should compile and run from the command line using the commands like ```sbt clean compile test``` and from the docker image. Naturally, you project should be IntelliJ friendly, i.e., your graders should be able to import your code into IntelliJ and run from there. Use .gitignore to exlude files that should not be pushed into the repo.

### Homework 3
The submission deadline is on Sunday, November 15 at 11PM CST via the bitbucket repository. The deliverable will contain the implementation of the algorithm Chord using 

#### Evaluation criteria
- the maximum grade for this homework is 10%. Points are subtracted from this maximum grade: for example, saying that 2% is lost if some requirement is not completed means that the resulting grade will be 10%-2% => 8%; if the core homework functionality does not work, no bonus points will be given. Bonuses will be given for the following implementation details.
- typed Akka Behaviors model is used in the implementation, not the OO one: up to 3% bonus;
- implement [Akka persistence with Cassandra](https://doc.akka.io/docs/akka-persistence-cassandra/current/index.html): up to 5% bonus.
- integrate your Scala simulator with a [statistical package called R](https://www.r-project.org/): up to 3% bonus;
- thorough documentation of your implementation and user-level manual with step-by-step commands: up to 3% bonus.

### Course Project
The submission deadline is on Thursday, December 10 at 11PM CST via the bitbucket repository. 

#### Evaluation criteria
- the maximum grade for this course project is 20%. Points are subtracted from this maximum grade: for example, saying that 2% is lost if some requirement is not completed means that the resulting grade will be 20%-2% => 18%; if the core project functionality does not work, no bonus points will be given;


#### Common Evaluation Guidelines
- the code does not work in that it does not produce a correct output or crashes: all points lost;
- the implementation does not use the cluster sharding actor deployment model: up to 10 points lost;
- mutable messages are constructed and passed among actors: up to 5 points lost;
- having less than five unit and/or integration tests that test the main functionality: up to 5% lost;
- missing comments and explanations from the program: up to 10% lost;
- logging is not used in the program: up to 10% lost;
- no docker image is available for the submission: up to 5% lost;
- no evidence of AWS deployment of your simulator: up to 5% lost;
- hardcoding the input values in the source code instead of using the suggested configuration libraries: up to 10% lost;
- no instructions in README.md on how to install and run your program: up to 10% lost;
- the documentation exists but it is insufficient to understand how you assembled and deployed all components of the cloud: up to 10% lost;
- the minimum grade for each of these assignments cannot be less than zero.

That's it, folks!