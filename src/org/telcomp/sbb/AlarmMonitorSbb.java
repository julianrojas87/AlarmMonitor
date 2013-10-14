package org.telcomp.sbb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.slee.ActivityContextInterface;
import javax.slee.Address;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;
import javax.slee.facilities.TimerEvent;
import javax.slee.facilities.TimerFacility;
import javax.slee.facilities.TimerID;
import javax.slee.facilities.TimerOptions;
import javax.slee.management.AlarmMBean;

import org.mobicents.slee.container.SleeContainer;
import org.telcomp.events.EndReconfigurationEvent;
import org.telcomp.events.StartReconfigurationEvent;

public abstract class AlarmMonitorSbb implements javax.slee.Sbb {
	
	ActivityContextInterface alarmAci;

	private TimerFacility timerFacility; //TimerFacility for setting up the timer options
	TimerID timerID; //timerID for obtain TimerID so the timer could be cleared when service ends
	boolean alarms=false; //Flag to determine when alarms are stored for first time. If true, the first ArrayList (tempAlarms) is not filled again
	boolean firstAlarm=false; //Flag to determine the first alarms in the SLEE when the AlarmMonitor Service is started and send those by the event
	
	//ArrayLists for store Alarms and compare between them	
	ArrayList<String> Alarms = new ArrayList<String>();
	ArrayList<String> tempAlarms = new ArrayList<String>();
	ArrayList<String> newAlarm = new ArrayList<String>();
	//private ActivityContextInterface timerAci;


	public void onServiceStartedEvent(javax.slee.serviceactivity.ServiceStartedEvent event, ActivityContextInterface aci) {
		System.out.println("Monitoring Alarms...");
		//Setting the timer options and parameters when the service start, timer will be active every 5 seconds
		this.timerFacility.setTimer(aci, null,System.currentTimeMillis() + 5000, 5000, 0, new TimerOptions());
	}

	public void onTimerEvent(TimerEvent event, ActivityContextInterface aci) {
		// Setting the ID of the current timer so we can clear it when the service is killed
		timerID = event.getTimerID();
		listenAlarms(aci);
	}

	//Method for processing and listen alarms in real time
	public void listenAlarms (ActivityContextInterface aci) {
		String[] params = { "java.lang.String" };

		try {
			//Setting a List for store the alarm(s) parameters
			int alarmsLenght = ((String[]) SleeContainer.lookupFromJndi().getMBeanServer().invoke(new ObjectName(AlarmMBean.OBJECT_NAME), "getAlarms", null, null)).length;
			String[] alarmsMessage = new String[100];
			String alarmDescriptor;
			String finalParams;
			String splitParams[];
			List<String> finalParameters = new ArrayList<String>();
			
			//alarmsLenght>0 means that there is one or more active alarms in SLEE so we proceed to manage alarms
			if (alarmsLenght > 0) {
				System.out.println("---------------------------------------------------------");
				
				//alarms=false means that is the first time the listenAlarms() method look for alarms so it stores the alarm IDs found in the tempAlarms ArrayList
				if(!alarms){
					for(int i=0;i<alarmsLenght;i++){
						tempAlarms.add(((String[]) SleeContainer.lookupFromJndi().getMBeanServer().invoke(new ObjectName(AlarmMBean.OBJECT_NAME),"getAlarms", null, null))[i]);}
					
					//firstAlarm=false means that is the first time the service get the alarms so here would go the implementation to fire the event with the first alarms found
					if(!firstAlarm){
						System.out.println("First alarms sent!");
						firstAlarm=true;
					}
					
					//If Alarms is empty, we store the content of tempAlarms in Alarms, so both can be compared
					if(Alarms.isEmpty()){
						Alarms = new ArrayList<String>(tempAlarms);
					}
					//Setting alarms to true mean that the service has started and the first alarms in the SLEE has been stored
					alarms=true;
					
				} else{
					//alarms=true means that alarms has been filled for first time so now, the alarms found will be stored in the Alarms ArrayList
					for(int i=0;i<alarmsLenght;i++){
						Alarms.add(((String[]) SleeContainer.lookupFromJndi().getMBeanServer().invoke(new ObjectName(AlarmMBean.OBJECT_NAME),"getAlarms", null, null))[i]);}			
				}
				
				//If size of tempAlarms is minor than Alarms size, it means that a new alarm has been raised (Because while both ArrayList has the same size, means both have the same IDs)
				//So, we copy the content of Alarms in newAlarm ArrayList, then compare it whit tempAlarms and remove the common IDs on both ArrayList, finally obtaining the ID of the new alarm 
				if(tempAlarms.size()<Alarms.size()){
					
					System.out.println("New alarm raised!!");
					newAlarm = new ArrayList<String>(Alarms);
					newAlarm.removeAll(tempAlarms);
					System.out.println("The new alarm have ID: "+newAlarm.toString());
					
					//Setting the args with the new alarm data
					Object[] args = { newAlarm.get(0) };
					//Getting the alarm descriptor for required alarm
					alarmDescriptor = SleeContainer.lookupFromJndi().getMBeanServer().invoke(new ObjectName(AlarmMBean.OBJECT_NAME),"getDescriptor", args, params).toString();
					//Filling the arrayMessage with each segment of the descriptor from the current alarm, separated by ,
					alarmsMessage = ((String[]) alarmDescriptor.split(","));
					
					//Filling the String with raw parameters from the current alarm, discarding the first 9 chars
					finalParams = (String)alarmsMessage[10].substring(9);
					//Filling the array with the parameters from finalParams separated by ;
					splitParams = finalParams.split(";");
					//Filling the list with each parameter from previous array
					finalParameters = new ArrayList<String>(Arrays.asList(splitParams));					
					
					//Inputs for Reconfiguration Service provided by Monitoring Service
					HashMap<String,Object> reconfigInputs = new HashMap<String,Object>();
					reconfigInputs.put("serviceName",finalParameters.get(0));
					reconfigInputs.put("operationName",finalParameters.get(1));
					reconfigInputs.put("mainControlFlow",finalParameters.get(2));
					
					//Dynamically filling the fields for branchControlFlows in the HashMap
					for(int j=3; j<finalParameters.size();j++){
						reconfigInputs.put("branchControlFlow"+Integer.toString(j-2),finalParameters.get(j));
						System.out.println("BRANCHS FROM CYCLE: "+finalParameters.get(j));
					}
					
					//Firing the event with required data in a HashMap
					StartReconfigurationEvent getAlarmEvent = new StartReconfigurationEvent(reconfigInputs);
					this.fireStartReconfigurationEvent(getAlarmEvent, aci, null);
					System.out.println("Start Alarm Tester Event fired!");
					
				} else{
					//If size is the same, means that there is no new alarms
					System.out.println("No new alarms...");
				}
				
				for(int i=0;i<tempAlarms.size();i++){
					System.out.println("actual tempALARMS: "+tempAlarms.get(i));
				}
				
				for(int j=0;j<Alarms.size();j++){
					System.out.println("actual alarALARMS: "+Alarms.get(j));
				}
				
				//Finally, we copy the content of Alarms to tempAlarms and clear the lists we will need empty for next iteration
				tempAlarms = new ArrayList<String>(Alarms);
				Alarms.clear();
				newAlarm.clear();
				finalParameters.clear();
				
			} else{
				//If there is no alarms or all the alarms has been cleared, we set the flags to the initial state and clear all ArrayLists
				System.out.println("There are no active alarms");
				alarms=false;
				firstAlarm=false;		
				tempAlarms.clear();
				Alarms.clear();
				newAlarm.clear();
			}

		} catch (Exception e) {
			System.out.println("Error en consulta JMX: ");
			e.printStackTrace();
		}
		

	}
	
	public void onEndAlarmTesterEvent(EndReconfigurationEvent event, ActivityContextInterface aci){
		
			System.out.println("Success finished Reconfiguration Proccess...");
			System.out.println(event.isSuccess());
			//aci.detach(this.sbbContext.getSbbLocalObject());
			
		
	}
	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			timerFacility = (TimerFacility) ctx.lookup("slee/facilities/timer");
		} catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void unsetSbbContext() {
		this.sbbContext = null;
	}

	// TODO: Implement the lifecycle methods if required
	public void sbbCreate() throws javax.slee.CreateException {
	}

	public void sbbPostCreate() throws javax.slee.CreateException {
	}

	public void sbbActivate() {
	}

	public void sbbPassivate() {
	}

	public void sbbRemove() {
		
		// Canceling the current timer just before the AlarmMonitor Service is stopped
		this.timerFacility.cancelTimer(timerID);
		//this.timerAci.detach(this.sbbContext.getSbbLocalObject());
		System.out.println("TIMER REMOVED, Alarm Monitor Service ENDED!");
	}

	public void sbbLoad() {
	}

	public void sbbStore() {
	}

	public void sbbExceptionThrown(Exception exception, Object event,
			ActivityContextInterface activity) {
	}

	public void sbbRolledBack(RolledBackContext context) {
	}
	
	public abstract void fireStartReconfigurationEvent (StartReconfigurationEvent event, ActivityContextInterface aci, Address address);

	/**
	 * Convenience method to retrieve the SbbContext object stored in
	 * setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove
	 * this method, the sbbContext variable and the variable assignment in
	 * setSbbContext().
	 * 
	 * @return this SBB's SbbContext object
	 */

	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	private SbbContext sbbContext; // This SBB's SbbContext

}
