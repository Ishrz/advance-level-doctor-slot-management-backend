


# 🩺 Advanced-Level Doctor Slot Management – Spring Boot Backend

This is a production-ready Spring Boot application for managing doctor appointment slots — built for assignment and aligned with real-world hospital scheduling systems.

---

## 🚀 Features Implemented

- ✅ Create/Edit/Delete time slots (daily, weekly, custom range)
- ✅ Block specific dates (holidays, emergencies)
- ✅ Slot types: consultation, surgery, breaks
- ✅ Slot locking mechanism (`PENDING` state)
- ✅ Smart slot recommendation engine
- ✅ Walk-in & emergency slot management
- ✅ Conflict prevention: double-booking detection
- ✅ Mark slots unavailable (vacation/leave)
- ✅ Booking rules engine (per day limits, buffer gaps)
- ✅ Audit log system for admin tracking
- ✅ PostgreSQL database integration

---

## ⚙️ Tech Stack

| Layer        | Tools                          |
|--------------|--------------------------------|
| Backend      | Java 1.8, Spring Boot          |
| ORM/DB       | Spring Data JPA, PostgreSQL    |
| Build Tool   | Maven                          |
| Dev Tools    | Spring Tool Suite (STS), Git   |
| Testing      | Postman, pgAdmin               |

---

## 📁 Folder Structure

DoctorSlotApi/
├── src/main/java/com/doctor/slot
│ ├── controller/
│ ├── model/
│ ├── repository/
│ └── service/
└── src/main/resources/
├── application.properties
└── ...



---

## 🔧 Configuration – application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/doctor_slot_db
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
server.port=8080
📬 Key API Endpoint

POST /api/v1/slot-management
All slot actions (create, delete, book, block, recommend, etc.) are handled via a single API using action in the request body.

🔸 Sample Request
json

{
  "action": "CREATE_SLOTS",
  "doctorId": 1,
  "startDate": "2025-06-21",
  "endDate": "2025-06-21",
  "slotDuration": 30,
  "slotType": "CONSULTATION",
  "location": "Clinic A",
  "notes": "Morning session"
}

📊 Audit Log Table (slot_audit_log)
Tracks all changes to slot data:

id, slotId, doctorId, action, timestamp, performedBy, message

👨‍💻 Developer Info
Name: Mubashir Ahamad Shaikh
Location: Pune, India
GitHub: github.com/Ishrz

🔮 Future Scope (pending)
Frontend dashboard (React + calendar view)

Redis-based concurrency locks

Email/SMS reminders

Slot analytics with charts

✅ Status
💯 Backend complete and tested
📦 Ready for frontend integration