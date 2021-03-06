package com.vmware.xclone.basicops;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;

import javax.xml.ws.soap.SOAPFaultException;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.PropertyChange;
import com.vmware.vim25.PropertyChangeOp;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertyFilterUpdate;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VimPortType;

public class BasicOps
{
	public static   Object[] waitForValues(ManagedObjectReference objmor,
			String[] filterProps,
			String[] endWaitProps,
			Object[][] expectedVals,ManagedObjectReference propCollectorRef,VimPortType vimPort)
					throws RemoteException,
					Exception {
		// version string is initially null
		String version = "";
		Object[] endVals = new Object[endWaitProps.length];
		Object[] filterVals = new Object[filterProps.length];

		PropertyFilterSpec spec = new PropertyFilterSpec();

		spec.getObjectSet().add(new ObjectSpec());

		spec.getObjectSet().get(0).setObj(objmor);

		spec.getPropSet().addAll(Arrays.asList(new PropertySpec[]{new PropertySpec()}));

		spec.getPropSet().get(0).getPathSet().addAll(Arrays.asList(filterProps));

		spec.getPropSet().get(0).setType(objmor.getType());

		//spec.getObjectSet().get(0).getSelectSet().add(null);

		spec.getObjectSet().get(0).setSkip(Boolean.FALSE);

		ManagedObjectReference filterSpecRef = vimPort.createFilter(propCollectorRef, spec, true);

		boolean reached = false;

		UpdateSet updateset = null;
		PropertyFilterUpdate[] filtupary = null;
		PropertyFilterUpdate filtup = null;
		ObjectUpdate[] objupary = null;
		ObjectUpdate objup = null;
		PropertyChange[] propchgary = null;
		PropertyChange propchg = null;
		while (!reached) {
			boolean retry = true;
			while (retry) {
				try {
					updateset = vimPort.waitForUpdates(propCollectorRef, version);
					retry = false;
				} catch (SOAPFaultException sfe) {
					sfe.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if(updateset != null) {
				version = updateset.getVersion();
			}
			if (updateset == null || updateset.getFilterSet() == null) {
				continue;
			}
			List<PropertyFilterUpdate> listprfup = updateset.getFilterSet();
			filtupary = listprfup.toArray(new PropertyFilterUpdate[listprfup.size()]);
			filtup = null;
			for (int fi = 0; fi < filtupary.length; fi++) {
				filtup = filtupary[fi];
				List<ObjectUpdate> listobjup = filtup.getObjectSet();
				objupary = listobjup.toArray(new ObjectUpdate[listobjup.size()]);
				objup = null;
				propchgary = null;
				for (int oi = 0; oi < objupary.length; oi++) {
					objup = objupary[oi];
					if (objup.getKind() == ObjectUpdateKind.MODIFY
							|| objup.getKind() == ObjectUpdateKind.ENTER
							|| objup.getKind() == ObjectUpdateKind.LEAVE) {
						List<PropertyChange> listchset = objup.getChangeSet();
						propchgary
						= listchset.toArray(new PropertyChange[listchset.size()]);
						for (int ci = 0; ci < propchgary.length; ci++) {
							propchg = propchgary[ci];
							updateValues(endWaitProps, endVals, propchg);
							updateValues(filterProps, filterVals, propchg);
						}
					}
				}
			}
			Object expctdval = null;
			// Check if the expected values have been reached and exit the loop if done.
			// Also exit the WaitForUpdates loop if this is the case.
			for (int chgi = 0; chgi < endVals.length && !reached; chgi++) {
				for (int vali = 0; vali < expectedVals[chgi].length
						&& !reached; vali++) {
					expctdval = expectedVals[chgi][vali];
					reached = expctdval.equals(endVals[chgi]) || reached;
				}
			}
		}

		// Destroy the filter when we are done.
		vimPort.destroyPropertyFilter(filterSpecRef);
		return filterVals;
	}



	private  static void updateValues(String[] props, Object[] vals, PropertyChange propchg) {
		for (int findi = 0; findi < props.length; findi++) {
			if (propchg.getName().lastIndexOf(props[findi]) >= 0) {
				if (propchg.getOp() == PropertyChangeOp.REMOVE) {
					vals[findi] = "";
				} else {
					vals[findi] = propchg.getVal();
				}
			}
		}
	}
}