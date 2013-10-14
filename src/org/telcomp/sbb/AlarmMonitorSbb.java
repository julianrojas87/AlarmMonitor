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
		ArrayList<String> tempAlarms = new ArrayList<String>();

		try {
			//Setting a List for store the alarm(s) parameters
			int alarmsLenght = ((String[]) SleeContainer.lookupFromJndi().getMBeanServer().invoke(new ObjectName(AlarmMBean.OBJECT_NAME), "getAlarms", null, null)).length;
			String[] alarmsMessage = new String[100];
			String alarmDescriptor;
			String finalParams;
			String splitParams[];
			List<String> finalParameters = new ArrayList<String>();
			boolean erased = false;
			
			//alarmsLenght>0 means that there is one or more active alarms in SLEE so we proceed to manage the alarms
			if (alarmsLenght > 0) {
				System.out.println("Processing new alarms...");
				for(int i=0;i<alarmsLenght;i++){
					tempAlarms.add(((String[]) SleeContainer.lookupFromJndi().getMBeanServer().
							invoke(new ObjectName(AlarmMBean.OBJECT_NAME),"getAlarms", null, null))[i]);
				}
				
				//Processing the active alarms for reconfiguration and then erase them
				for(String alarm : tempAlarms){
					Object[] args = { alarm };
					//Getting the alarm descriptor for required alarm
					alarmDescriptor = SleeContainer.lookupFromJndi().getMBeanServer().
							invoke(new ObjectName(AlarmMBean.OBJECT_NAME),"getDescriptor", args, params).toString();
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
					}

					//Firing the event with required data in a HashMap
					StartReconfigurationEvent getAlarmEvent = new StartReconfigurationEvent(reconfigInputs);
					this.fireStartReconfigurationEvent(getAlarmEvent, aci, null);
					System.out.println("Start Reconfiguration Event fired!");
					
					erased = (boolean) SleeContainer.lookupFromJndi().getMBeanServer().invoke(new ObjectName(AlarmMBean.OBJECT_NAME), "clearAlarm", args, params);
					System.out.println("Alarm cleared: "+erased);
				}
			}
			
		} catch (Exception e) {
			System.out.println("Error en consulta JMX: ");
			e.printStackTrace();
		}
	}
	
	public void onEndReconfigurationEvent(EndReconfigurationEvent event, ActivityContextInterface aci){
			System.out.println("Success finished Reconfiguration Proccess...");
			System.out.println(event.isSuccess());
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
