/**
 * 
 */

/**
 * @author gdudhwal
 *
 */
 
 /*import org.linkedin.glu.agent.api.ShellExecException
 import org.linkedin.glu.agent.api.ScriptExecutionException
 import org.linkedin.glu.agent.api.ScriptExecutionCauseException
 */

 class faulttolerance {
	def String NodeName
	def String ServiceName
	def String NextNodeName

	def start = {
		log.info("starting load balancing")
		timers.schedule(timer: loadbalance, repeatFrequency: "15s")
	}

	def stop = {
		timers.cancel(timer: loadbalance)
	}

	def NodeNameList = ["sv-sigiri1", "sv-sigiri2", "sv-sigiri3", "sv-sigiri4", "sv-sigiri5"]

	def loadbalance = {
		//This the main function.
		log.info("starting load balance method")
		log.warn("warning :starting load balance method")
		gennode1()
	}

	def gennode1 = {
		log.info("started gennode1")

		def a = 1;
		NodeNameList.each
		{
			log.info("$NodeNameList")
			log.info("it = ${it}")
			NodeName = it as String
			log.info("${NodeName}")

			if (isServerAvail()) { // JV: Is this logic correct? False means server is up, not down
				//Now server is not availaible. Starting load balancing mechanism
				
				log.info("Server has gone down, Now starting fault tolerance")

				doRetry()
			}
		}
	}

	def boolean isServiceDown() {
		ServRunning() == null
	}

	public void safestart() {
		ScriptExecutionCauseException ex = scriptShouldFail {
			start()
		}
	}

	public void safeloadbalance() {
		ScriptExecutionCauseException vx = scriptShouldFail {
			loadbalance()
		}
	}
	
	def boolean isServerAvail() {
		log.info("Started isServerAvail")
		isServerup() == null // JV: If == null, server is down, not available
		log.info("Ending isServerAvail")
	}

	// JV: This is doing the same function as isStale* method. Combine them into one.
	def Integer isServerup() {
		log.info("started isServerup()")

		//check if server is in stale list for this service.
		// JV: Is stale list = down server list?
		// JV: Scope of this definition doesn't seem correct -OR-
		// this list is not required because at most only one server can be in down state w/o some type of a loop
		def DownServList = []
		def resp_ssh =
			log.info("NodeName = ${NodeName}")
		// JV: what does glucheck.sh do? Server level check OR services on a server OR all services on a server?
		resp_ssh = shell.exec("sh /cacheDir/glu_scripts/glucheck.sh  ${NodeName} ")

		log.info("${resp_ssh}")
		if (resp_ssh < 0) {
			log.info("${NodeName} server is up and running")
			return resp_ssh
		} else {
			log.info("Adding server to down server list")
			DownServList << NodeName // add server to a down server list
			log.info("Server ${NodeName} has been flagged as Down Server ")
			log.info("Shifting running services to another node.")
			return null
		}
	}
	
	def doRetry = {
		// JV: There seems to be a flaw in the logic here. The requirement is to maintain a *total* # of service instances
		// across the machines in a data center. Here, the program is looking for a # of service instance *per server*.
		// This implies at least one instance of every service must run on each server, which is not a requirement.
		log.info("started Do retry")

		log.info("Started ServRunning passing ${NodeName} ${params.servicename}")
		shell.waitFor(timeout: '30s', heartbeat: '60s') { duration ->
			def output = shell.exec(" sh /cacheDir/glu_scripts/load_service_monitor.sh ${NodeName} ${params.service}")
			log.info "${output}"

			if (output == 0) {
				log.warn("No ${params.service} is running on this server.")
				output = 0
			} else {
				log.info("${output} no of services are running")
			}
			//Check for Down service.
			def reqServiceNo = params.reqServiceNo
			log.info(" got output  ${reqServiceNo}")
			if (output < reqServiceNo) {
				log.info("starting to shift service to stale node")
				shiftServ()
				log.info("Shifting service to stale node")
			} else
			//Do nothing
				log.info("Current running services are with in plan so doing nothing")
		}
	}
	def shiftServ = {
		log.info("started shifserv()")
		// This function will shift service
		//1. Check if stale Node is availaible

		def staleNode = params.stalenode

		if (isStaleServerAvail()) {
			log.warn("${staleNode} server is down which is in stale node list now moving to next Node")
			//shiftNextNode()

		} else {
			log.info("Shifting Service to stale node")
			shiftstaleNode()
		}
	}

	def boolean isStaleServerAvail() {
		log.info("started isStaleServerAvail")
		isstaleServerup() == null
	}
	
	def Integer isstaleServerup() {
		log.info("started isstaleServerup()")

		//check if stale node is up


		def resp_staleNode =
			log.info("stale Node = ${params.stalenode}")
		resp_staleNode = shell.exec("sh /cacheDir/glu_scripts/glucheck.sh  ${params.stalenode}")

		log.info("${resp_staleNode}")
		if (resp_staleNode > 0) {
			log.info("${params.stalenode} server is up and running")
			return resp_staleNode
		} else {
			log.info("stale node is down ")
			return null
		}
	}
	
	// JV: Checks if server is...what?
	
	def shiftstaleNode = {
		
				// This funtion will shift the service to stale node.
		
				//1. we ll check wheather the port is availaible or not.
				log.info("started shiftstaleNode")
				// JV: The function name isPortAvailonStaleNode() doesn't make sense. if isPortAvailonStaleNode returns true,
				// it means the port # was null, not a valid port #, yes? If so, logic inside isPortAvailonStaleNode function
				// should be inverted
				if (isPortAvailonStaleNode()) {
					//Port is availaible. We ll shift to this.
					log.info("started service on stale node")
				} else {
					startServiceonStaleNode()
					log.info("port on stale isn't free")
				}
			}
	

	def boolean isPortAvailonStaleNode() {
		checkPortAvailonStaleNode() == null
	}
	
	def portList = ["9701", "9702", "9703"]

	def checkPortAvailonStaleNode = {
		def portAvail = 0
		portList.each()
				{
					// JV: variable names are mixed up. servState should hold result of getservState.sh and vice versa?
					def entryState = shell.exec("sh /cacheDir/glu_scripts/getentryState.sh ${params.stalenode} ${params.servicename} ${it}")
					def servState = shell.exec("sh /cacheDir/glu_scripts/getservState.sh ${params.stalenode} ${params.servicename} ${it}")
					if (entryState == 'running' && servState == 'Started') {
						//Do nothing
						portAvail = 0
					} else
						portAvail = it
				}

		if (portAvail != '0') {
			return portAvail
		} else
			return null
		log.info("portAvail=${portAvail}")
	}


	def startServiceonStaleNode = {
		log.info("started startServiceStaleNode")
		// JV: Why is this being called again. It was just called for isPortAvailonStaleNode earlier.
		def portToRun = checkPortAvailonStaleNode()

		// Got port now starting the service.

		//1. we create a plan using curl and REST api.

		def plans = shell.exec("sh /cacheDir/glu_scripts/genPlan.sh ${params.stalenode} ${params.servicename} ${portToRun}")

		//2. Execute the plan
		shell.exec(" sh /cacheDir/glu_scripts/execPlan.sh ${plans} ")
		//3. Plan has been executed now. Service should be started now.
		// JV: Trust, but verify :) Do an explicit check that service indeed started at ${portToRun} on ${params.stalenode}
		log.info("${params.servicename} Service has been started on ${params.stalenode} using ${portToRun} port")
	}

 
 def shiftNextNode = {
	 log.info ("Shifting service to Next Node")
	 def a =1;
	 NodeName.each
		 {
			 log.info("$NodeNameList")
			 log.info("it = ${it}")
			 NextNodeName = it as String
			 log.info("${NextNodeName}")
				 //Here we search for a stale port on each every node.
				 if (isNextNodeAvail()) {
				 log.warn ("${NextNodeName} is not availaible")
				 }
				 else
				 {
				 log.info("Shifting service to ${NextNodeName}")
				 shiftonNextNode()
				 }
				 
		 }
   }
 
 def boolean isNextNodeAvail() {
	 log.info("started isNextNodeAvail")
	 isNextNodeup() == null
 }
 
 def Integer isNextNodeup() {
	 log.info("started isNextNodeup()")

	 //check if next node is up


	 def resp_NextNode =
		 log.info("Next Node = ${NextNodeName}")
	 resp_NextNode = shell.exec("sh /cacheDir/glu_scripts/glucheck.sh  ${NextNodeName}")

	 log.info("${resp_NextNode}")
	 if (resp_NextNode > 0) {
		 log.info("${NextNodeName} server is up and running")
		 return resp_NextNode
	 } else {
		 log.info("Next node is down ")
		 return null
	 }
 }
 
 // JV: Checks if server is...what?
 
 def shiftonNextNode = {
	 
			 // This funtion will shift the service to stale node.
	 
			 //1. we ll check wheather the port is availaible or not.
			 log.info("started shiftonNextNode")
			 // JV: The function name isPortAvailonStaleNode() doesn't make sense. if isPortAvailonStaleNode returns true,
			 // it means the port # was null, not a valid port #, yes? If so, logic inside isPortAvailonStaleNode function
			 // should be inverted
			 if (isPortAvailonStaleNode()) {
				 //Port is availaible. We ll shift to this.
				 log.info("started service on stale node")
			 } else {
				 startServiceonNextNode()
				 log.info("port on stale isn't free")
			 }
		 }
 

 def boolean isPortAvailonNextNode() {
	 checkPortAvailonNextNode() == null
 }
 
 def portList = ["9701", "9702", "9703"]

 def checkPortAvailonNextNode = {
	 def portAvail = 0
	 portList.each()
			 {
				 // JV: variable names are mixed up. servState should hold result of getservState.sh and vice versa?
				 def entryState = shell.exec("sh /cacheDir/glu_scripts/getentryState.sh ${NextNodeName} ${params.servicename} ${it}")
				 def servState = shell.exec("sh /cacheDir/glu_scripts/getservState.sh ${NextNodeName} ${params.servicename} ${it}")
				 if (entryState == 'running' && servState == 'Started') {
					 //Do nothing
					 portAvail = 0
				 } else
					 portAvail = it
			 }

	 if (portAvail != '0') {
		 return portAvail
	 } else
		 return null
	 log.info("portAvail=${portAvail}")
 }


 def startServiceonNextNode = {
	 log.info("started startServiceNextNode")
	 // JV: Why is this being called again. It was just called for isPortAvailonStaleNode earlier.
	 def portToRun = checkPortAvailonNextNode()

	 // Got port now starting the service.

	 //1. we create a plan using curl and REST api.

	 def plans = shell.exec("sh /cacheDir/glu_scripts/genPlan.sh ${NextNodeName} ${params.servicename} ${portToRun}")

	 //2. Execute the plan
	 shell.exec(" sh /cacheDir/glu_scripts/execPlan.sh ${plans} ")
	 //3. Plan has been executed now. Service should be started now.
	 // JV: Trust, but verify :) Do an explicit check that service indeed started at ${portToRun} on ${NextNodeName}
	 log.info("${params.servicename} Service has been started on ${NextNodeName} using ${portToRun} port")
 }
}


