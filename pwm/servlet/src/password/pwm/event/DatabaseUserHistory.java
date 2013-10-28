/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.event;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;

import java.util.ArrayList;
import java.util.List;

public class DatabaseUserHistory implements UserHistory {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(DatabaseUserHistory.class);

    private static final DatabaseTable TABLE = DatabaseTable.USER_AUDIT;

    final PwmApplication pwmApplication;
    final DatabaseAccessor databaseAccessor;

    public DatabaseUserHistory(final PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
        this.databaseAccessor = pwmApplication.getDatabaseAccessor();
    }

    @Override
    public void updateUserHistory(AuditRecord auditRecord) throws PwmUnrecoverableException {
        final String targetUserDN = auditRecord.getTargetDN();
        final String guid;
        try {
            guid = Helper.readLdapGuidValue(pwmApplication, targetUserDN);
        } catch (ChaiUnavailableException e) {
            LOGGER.error("unable to read guid for user '" + targetUserDN + "', cannot update user history, error: " + e.getMessage());
            return;
        }

        try {
            final StoredHistory storedHistory;
            storedHistory = readStoredHistory(guid);
            storedHistory.getRecords().add(auditRecord);
            writeStoredHistory(guid,storedHistory);
        } catch (DatabaseException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,e.getMessage()));
        }
    }

    @Override
    public List<AuditRecord> readUserHistory(UserInfoBean userInfoBean) throws PwmUnrecoverableException {
        final String userGuid = userInfoBean.getUserGuid();
        try {
            return readStoredHistory(userGuid).getRecords();
        } catch (DatabaseException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,e.getMessage()));
        }
    }

    private StoredHistory readStoredHistory(final String guid) throws DatabaseException {
        final String str = this.databaseAccessor.get(TABLE, guid);
        if (str == null || str.length() < 1) {
            return new StoredHistory();
        }
        return Helper.getGson().fromJson(str,StoredHistory.class);
    }

    private void writeStoredHistory(final String guid, final StoredHistory storedHistory) throws DatabaseException {
        if (storedHistory == null) {
            return;
        }
        final String str = Helper.getGson().toJson(storedHistory);
        databaseAccessor.put(TABLE,guid,str);
    }

    static class StoredHistory {
        private List<AuditRecord> records = new ArrayList<AuditRecord>();

        List<AuditRecord> getRecords() {
            return records;
        }

        void setRecords(List<AuditRecord> records) {
            this.records = records;
        }
    }
}