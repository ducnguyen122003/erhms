# Đối chiếu 121 mã màn với mã nguồn

Gồm 108 mã từ bộ thiết kế, 12 màn danh mục và 1 màn cấp tài khoản học viên bổ sung theo yêu cầu.

Quyền hợp đồng được cập nhật theo V12: [MANAGER_TRAINING_CONTRACTS.md](MANAGER_TRAINING_CONTRACTS.md).

V13 chuyển toàn bộ màn quản lý ngành nghề và khóa học sang Admin: [MASTER_DATA.md](MASTER_DATA.md). Khởi động lại backend để áp dụng migration, rồi tải lại frontend.

Nguồn: ma trận Excel và `Screen_Manifest.json` trong bộ SVG. Mỗi mã dưới đây có route/component; tab, popup và trạng thái dùng chung bộ xử lý nghiệp vụ. Đăng nhập và khôi phục giữ luồng OTP 6 số đã triển khai theo yêu cầu trước đó.

| FE | Mã màn | Route | File giao diện | Vai trò mặc định |
| --- | --- | --- | --- | --- |
| FE-21 | `Dashboard_Admin` | `/dashboard/admin` | [AdminDashboard.jsx](../G5_FE1/src/dashboards/AdminDashboard.jsx) | ADMIN |
| FE-21 | `Dashboard_Lecturer` | `/dashboard/lecturer` | [LecturerDashboard.jsx](../G5_FE1/src/dashboards/LecturerDashboard.jsx) | LECTURER |
| FE-21 | `Dashboard_Student` | `/dashboard/student` | [StudentDashboard.jsx](../G5_FE1/src/dashboards/StudentDashboard.jsx) | STUDENT |
| FE-21 | `Dashboard_Accountant` | `/dashboard/accountant` | [AccountantDashboard.jsx](../G5_FE1/src/dashboards/AccountantDashboard.jsx) | ACCOUNTANT |
| FE-21 | `Dashboard_IT_Support` | `/dashboard/it-support` | [ITSupportDashboard.jsx](../G5_FE1/src/dashboards/ITSupportDashboard.jsx) | IT_SUPPORT |
| FE-21 | `Dashboard_Student_Manager` | `/dashboard/manager` | [ManagerDashboard.jsx](../G5_FE1/src/dashboards/ManagerDashboard.jsx) | MANAGER |
| FE-21 | `Dashboard_Admissions` | `/dashboard/hr` | [HRDashboard.jsx](../G5_FE1/src/dashboards/HRDashboard.jsx) | HR |
| FE-01 | `Login` | `/login` | [Login.jsx](../G5_FE1/src/Login.jsx) | Công khai |
| FE-01 | `ForgetPassword` | `/login` | [Login.jsx](../G5_FE1/src/Login.jsx) | Công khai |
| FE-01 | `ForgetPassword_Sent` | `/login` | [Login.jsx](../G5_FE1/src/Login.jsx) | Công khai |
| FE-01 | `ResetPassword` | `/login` | [Login.jsx](../G5_FE1/src/Login.jsx) | Công khai |
| FE-01 | `ChangePassword` | `/screens/ChangePassword` | [ChangePassword.jsx](../G5_FE1/src/screens/pages/ChangePassword.jsx) | ADMIN, LECTURER, STUDENT, ACCOUNTANT, IT_SUPPORT, MANAGER, HR |
| FE-02 | `ListUser` | `/admin/users` | [UserAccounts.jsx](../G5_FE1/src/admin/UserAccounts.jsx) | ADMIN |
| FE-02 | `UserProfile` | `/profile` | [UserProfile.jsx](../G5_FE1/src/UserProfile.jsx) | ADMIN, LECTURER, STUDENT, ACCOUNTANT, IT_SUPPORT, MANAGER, HR |
| FE-02 | `EditUser` | `/admin/users/{id}/edit` | [UserEditPage.jsx](../G5_FE1/src/admin/UserEditPage.jsx) | ADMIN |
| FE-02 | `RolePermission` | `/admin/permissions` | [RolePermissionsPage.jsx](../G5_FE1/src/admin/RolePermissionsPage.jsx) | ADMIN |
| FE-02 | `SystemConfiguration` | `/admin/settings` | [SystemSettingsPage.jsx](../G5_FE1/src/admin/SystemSettingsPage.jsx) | ADMIN |
| FE-02 | `SystemAuditLog` | `/admin/audit-logs` | [SystemAuditPage.jsx](../G5_FE1/src/admin/SystemAuditPage.jsx) | ADMIN |
| FE-03 | `HomePage` | `/public/HomePage` | [HomePage.jsx](../G5_FE1/src/screens/public/HomePage.jsx) | Công khai |
| FE-03 | `AboutUs` | `/public/AboutUs` | [AboutUs.jsx](../G5_FE1/src/screens/public/AboutUs.jsx) | Công khai |
| FE-03 | `Professions` | `/public/Professions` | [Professions.jsx](../G5_FE1/src/screens/public/Professions.jsx) | Công khai |
| FE-03 | `Train` | `/public/Train` | [Train.jsx](../G5_FE1/src/screens/public/Train.jsx) | Công khai |
| FE-03 | `DevelopmentRoadmap` | `/public/DevelopmentRoadmap` | [DevelopmentRoadmap.jsx](../G5_FE1/src/screens/public/DevelopmentRoadmap.jsx) | Công khai |
| FE-03 | `PartnerEnterprise` | `/public/PartnerEnterprise` | [PartnerEnterprise.jsx](../G5_FE1/src/screens/public/PartnerEnterprise.jsx) | Công khai |
| FE-03 | `Contact` | `/public/Contact` | [Contact.jsx](../G5_FE1/src/screens/public/Contact.jsx) | Công khai |
| FE-03 | `PublicCourseDetail` | `/public/PublicCourseDetail/{id}` | [PublicCourseDetail.jsx](../G5_FE1/src/screens/public/PublicCourseDetail.jsx) | Công khai |
| FE-03 | `Contact_Submitted` | `/public/Contact_Submitted` | [Contact_Submitted.jsx](../G5_FE1/src/screens/public/Contact_Submitted.jsx) | Công khai |
| FE-04 | `ListAdmissionApplication` | `/screens/ListAdmissionApplication` | [ListAdmissionApplication.jsx](../G5_FE1/src/screens/pages/ListAdmissionApplication.jsx) | HR |
| FE-04 | `ListAdmissionApplicationDetail` | `/screens/ListAdmissionApplicationDetail/{id}` | [ListAdmissionApplicationDetail.jsx](../G5_FE1/src/screens/pages/ListAdmissionApplicationDetail.jsx) | HR |
| FE-04 | `AdmissionConsultation` | `/screens/AdmissionConsultation/{id}` | [AdmissionConsultation.jsx](../G5_FE1/src/screens/pages/AdmissionConsultation.jsx) | HR |
| FE-05 | `ListStudent` | `/screens/ListStudent` | [ListStudent.jsx](../G5_FE1/src/screens/pages/ListStudent.jsx) | MANAGER |
| FE-05 | `StudentProfileDetail` | `/screens/StudentProfileDetail/{id}` | [StudentProfileDetail.jsx](../G5_FE1/src/screens/pages/StudentProfileDetail.jsx) | MANAGER |
| FE-05 | `EditStudentProfile` | `/screens/EditStudentProfile/{id}` | [EditStudentProfile.jsx](../G5_FE1/src/screens/pages/EditStudentProfile.jsx) | MANAGER |
| FE-06/20 | `ListNotification` | `/screens/ListNotification` | [ListNotification.jsx](../G5_FE1/src/screens/pages/ListNotification.jsx) | ADMIN, LECTURER, STUDENT, ACCOUNTANT, IT_SUPPORT, MANAGER, HR |
| FE-06 | `ViewNotification` | `/screens/ViewNotification/{id}` | [ViewNotification.jsx](../G5_FE1/src/screens/pages/ViewNotification.jsx) | ADMIN, LECTURER, STUDENT, ACCOUNTANT, IT_SUPPORT, MANAGER, HR |
| FE-06 | `SendNotification` | `/screens/SendNotification` | [SendNotification.jsx](../G5_FE1/src/screens/pages/SendNotification.jsx) | MANAGER |
| FE-07 | `ListTrainingContract` | `/screens/ListTrainingContract` | [ListTrainingContract.jsx](../G5_FE1/src/screens/pages/ListTrainingContract.jsx) | ACCOUNTANT, MANAGER |
| FE-07 | `AddTrainingContract` | `/screens/AddTrainingContract` | [AddTrainingContract.jsx](../G5_FE1/src/screens/pages/AddTrainingContract.jsx) | MANAGER |
| FE-07 | `EditTrainingContract` | `/screens/EditTrainingContract/{id}` | [EditTrainingContract.jsx](../G5_FE1/src/screens/pages/EditTrainingContract.jsx) | MANAGER |
| FE-07 | `TrainingContractDetail` | `/screens/TrainingContractDetail/{id}` | [TrainingContractDetail.jsx](../G5_FE1/src/screens/pages/TrainingContractDetail.jsx) | ACCOUNTANT, MANAGER |
| FE-07 | `ContractSignature` | `/screens/ContractSignature/{id}` | [ContractSignature.jsx](../G5_FE1/src/screens/pages/ContractSignature.jsx) | MANAGER |
| FE-08 | `ListCourse` | `/screens/ListCourse` | [ListCourse.jsx](../G5_FE1/src/screens/pages/ListCourse.jsx) | ADMIN |
| FE-08 | `AddCourse` | `/screens/AddCourse` | [AddCourse.jsx](../G5_FE1/src/screens/pages/AddCourse.jsx) | ADMIN |
| FE-08 | `EditCourse` | `/screens/EditCourse/{id}` | [EditCourse.jsx](../G5_FE1/src/screens/pages/EditCourse.jsx) | ADMIN |
| FE-08 | `CourseDetail` | `/screens/CourseDetail/{id}` | [CourseDetail.jsx](../G5_FE1/src/screens/pages/CourseDetail.jsx) | ADMIN |
| FE-09 | `ListClass` | `/screens/ListClass` | [ListClass.jsx](../G5_FE1/src/screens/pages/ListClass.jsx) | MANAGER |
| FE-09 | `ClassDetail` | `/screens/ClassDetail/{id}` | [ClassDetail.jsx](../G5_FE1/src/screens/pages/ClassDetail.jsx) | MANAGER |
| FE-09 | `EditClass` | `/screens/EditClass/{id}` | [EditClass.jsx](../G5_FE1/src/screens/pages/EditClass.jsx) | MANAGER |
| FE-09 | `AutoAssignmentReview` | `/screens/AutoAssignmentReview` | [AutoAssignmentReview.jsx](../G5_FE1/src/screens/pages/AutoAssignmentReview.jsx) | MANAGER |
| FE-09 | `AutoAssignmentDetail` | `/screens/AutoAssignmentDetail/{id}` | [AutoAssignmentDetail.jsx](../G5_FE1/src/screens/pages/AutoAssignmentDetail.jsx) | MANAGER |
| FE-10 | `TimeTable_Manager` | `/screens/TimeTable_Manager` | [TimeTable_Manager.jsx](../G5_FE1/src/screens/pages/TimeTable_Manager.jsx) | MANAGER |
| FE-10 | `TimeTable_Lecturer` | `/screens/TimeTable_Lecturer` | [TimeTable_Lecturer.jsx](../G5_FE1/src/screens/pages/TimeTable_Lecturer.jsx) | LECTURER |
| FE-10 | `TimeTable_Student` | `/screens/TimeTable_Student` | [TimeTable_Student.jsx](../G5_FE1/src/screens/pages/TimeTable_Student.jsx) | STUDENT |
| FE-10 | `TimeTable_EditSession` | `/screens/TimeTable_EditSession[/{id}]` | [TimeTable_EditSession.jsx](../G5_FE1/src/screens/pages/TimeTable_EditSession.jsx) | MANAGER |
| FE-10 | `TimeTable_Conflict` | `/screens/TimeTable_Conflict/{id}` | [TimeTable_Conflict.jsx](../G5_FE1/src/screens/pages/TimeTable_Conflict.jsx) | MANAGER |
| FE-11 | `ListAttendance` | `/screens/ListAttendance` | [ListAttendance.jsx](../G5_FE1/src/screens/pages/ListAttendance.jsx) | LECTURER, MANAGER |
| FE-11 | `ClassAttendance` | `/screens/ClassAttendance/{id}` | [ClassAttendance.jsx](../G5_FE1/src/screens/pages/ClassAttendance.jsx) | LECTURER, MANAGER |
| FE-11 | `Attendance_Correction` | `/screens/Attendance_Correction/{id}` | [Attendance_Correction.jsx](../G5_FE1/src/screens/pages/Attendance_Correction.jsx) | LECTURER |
| FE-11 | `StudentAttendance` | `/screens/StudentAttendance` | [StudentAttendance.jsx](../G5_FE1/src/screens/pages/StudentAttendance.jsx) | STUDENT |
| FE-12 | `ClassGradebook` | `/screens/ClassGradebook/{id}` | [ClassGradebook.jsx](../G5_FE1/src/screens/pages/ClassGradebook.jsx) | LECTURER, MANAGER |
| FE-12 | `StudentResults` | `/screens/StudentResults` | [StudentResults.jsx](../G5_FE1/src/screens/pages/StudentResults.jsx) | STUDENT |
| FE-13 | `ListEquipmentRequest` | `/screens/ListEquipmentRequest` | [ListEquipmentRequest.jsx](../G5_FE1/src/screens/pages/ListEquipmentRequest.jsx) | LECTURER |
| FE-13 | `AddEquipmentRequest` | `/screens/AddEquipmentRequest` | [AddEquipmentRequest.jsx](../G5_FE1/src/screens/pages/AddEquipmentRequest.jsx) | LECTURER |
| FE-13 | `EquipmentRequestDetail_Lecturer` | `/screens/EquipmentRequestDetail_Lecturer/{id}` | [EquipmentRequestDetail_Lecturer.jsx](../G5_FE1/src/screens/pages/EquipmentRequestDetail_Lecturer.jsx) | LECTURER |
| FE-13 | `EquipmentRequestDetail_IT` | `/screens/EquipmentRequestDetail_IT/{id}` | [EquipmentRequestDetail_IT.jsx](../G5_FE1/src/screens/pages/EquipmentRequestDetail_IT.jsx) | IT_SUPPORT |
| FE-13 | `ListEquipmentRequest_IT` | `/screens/ListEquipmentRequest_IT` | [ListEquipmentRequest_IT.jsx](../G5_FE1/src/screens/pages/ListEquipmentRequest_IT.jsx) | IT_SUPPORT |
| FE-13 | `AssetHandover` | `/screens/AssetHandover/{id}` | [AssetHandover.jsx](../G5_FE1/src/screens/pages/AssetHandover.jsx) | IT_SUPPORT |
| FE-13 | `AssetHandover_Receipt` | `/screens/AssetHandover_Receipt/{id}` | [AssetHandover_Receipt.jsx](../G5_FE1/src/screens/pages/AssetHandover_Receipt.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-14 | `ListTuitionManagement` | `/screens/ListTuitionManagement` | [ListTuitionManagement.jsx](../G5_FE1/src/screens/pages/ListTuitionManagement.jsx) | ACCOUNTANT |
| FE-14 | `TuitionDetail` | `/screens/TuitionDetail/{id}` | [TuitionDetail.jsx](../G5_FE1/src/screens/pages/TuitionDetail.jsx) | ACCOUNTANT |
| FE-14 | `TuitionDetail_Student` | `/screens/TuitionDetail_Student/{id}` | [TuitionDetail_Student.jsx](../G5_FE1/src/screens/pages/TuitionDetail_Student.jsx) | STUDENT |
| FE-14 | `PaymentStatus_Admissions` | `/screens/PaymentStatus_Admissions` | [PaymentStatus_Admissions.jsx](../G5_FE1/src/screens/pages/PaymentStatus_Admissions.jsx) | MANAGER, HR |
| FE-15 | `ListPayment` | `/screens/ListPayment` | [ListPayment.jsx](../G5_FE1/src/screens/pages/ListPayment.jsx) | ACCOUNTANT |
| FE-15 | `PaymentDetail` | `/screens/PaymentDetail/{id}` | [PaymentDetail.jsx](../G5_FE1/src/screens/pages/PaymentDetail.jsx) | ACCOUNTANT |
| FE-15 | `PaymentDetail_Mismatch` | `/screens/PaymentDetail_Mismatch/{id}` | [PaymentDetail_Mismatch.jsx](../G5_FE1/src/screens/pages/PaymentDetail_Mismatch.jsx) | ACCOUNTANT |
| FE-15 | `PaymentDetail_Duplicate` | `/screens/PaymentDetail_Duplicate/{id}` | [PaymentDetail_Duplicate.jsx](../G5_FE1/src/screens/pages/PaymentDetail_Duplicate.jsx) | ACCOUNTANT |
| FE-15 | `PaymentInstruction` | `/screens/PaymentInstruction/{id}` | [PaymentInstruction.jsx](../G5_FE1/src/screens/pages/PaymentInstruction.jsx) | STUDENT, ACCOUNTANT, HR |
| FE-15 | `DepositList` | `/screens/DepositList` | [DepositList.jsx](../G5_FE1/src/screens/pages/DepositList.jsx) | ACCOUNTANT |
| FE-15 | `DepositRefund` | `/screens/DepositRefund/{id}` | [DepositRefund.jsx](../G5_FE1/src/screens/pages/DepositRefund.jsx) | ACCOUNTANT |
| FE-16 | `ListAsset` | `/screens/ListAsset` | [ListAsset.jsx](../G5_FE1/src/screens/pages/ListAsset.jsx) | IT_SUPPORT |
| FE-16 | `AddAsset` | `/screens/AddAsset` | [AddAsset.jsx](../G5_FE1/src/screens/pages/AddAsset.jsx) | IT_SUPPORT |
| FE-16 | `EditAsset` | `/screens/EditAsset/{id}` | [EditAsset.jsx](../G5_FE1/src/screens/pages/EditAsset.jsx) | IT_SUPPORT |
| FE-16 | `AssetDetail` | `/screens/AssetDetail/{id}` | [AssetDetail.jsx](../G5_FE1/src/screens/pages/AssetDetail.jsx) | IT_SUPPORT |
| FE-16 | `HistoryAssetAssignment` | `/screens/HistoryAssetAssignment` | [HistoryAssetAssignment.jsx](../G5_FE1/src/screens/pages/HistoryAssetAssignment.jsx) | IT_SUPPORT |
| FE-16 | `AssetMaintenance` | `/screens/AssetMaintenance/{id}` | [AssetMaintenance.jsx](../G5_FE1/src/screens/pages/AssetMaintenance.jsx) | IT_SUPPORT |
| FE-16 | `StudentAssets` | `/screens/StudentAssets/{id}` | [StudentAssets.jsx](../G5_FE1/src/screens/pages/StudentAssets.jsx) | STUDENT |
| FE-16 | `StudentAssetReceipt` | `/screens/StudentAssetReceipt/{id}` | [StudentAssetReceipt.jsx](../G5_FE1/src/screens/pages/StudentAssetReceipt.jsx) | STUDENT |
| FE-17 | `ListAssetRecovery` | `/screens/ListAssetRecovery` | [ListAssetRecovery.jsx](../G5_FE1/src/screens/pages/ListAssetRecovery.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-17 | `ListAssetDetail` | `/screens/ListAssetDetail/{id}` | [ListAssetDetail.jsx](../G5_FE1/src/screens/pages/ListAssetDetail.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-17 | `Recovery_Withdrawal` | `/screens/Recovery_Withdrawal/{id}` | [Recovery_Withdrawal.jsx](../G5_FE1/src/screens/pages/Recovery_Withdrawal.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-18 | `ListAssetIncident` | `/screens/ListAssetIncident` | [ListAssetIncident.jsx](../G5_FE1/src/screens/pages/ListAssetIncident.jsx) | ACCOUNTANT, IT_SUPPORT |
| FE-18 | `AssetIncidentDetail` | `/screens/AssetIncidentDetail/{id}` | [AssetIncidentDetail.jsx](../G5_FE1/src/screens/pages/AssetIncidentDetail.jsx) | ACCOUNTANT, IT_SUPPORT |
| FE-18 | `AssetIncidentDetail_Accountant` | `/screens/AssetIncidentDetail_Accountant/{id}` | [AssetIncidentDetail_Accountant.jsx](../G5_FE1/src/screens/pages/AssetIncidentDetail_Accountant.jsx) | ACCOUNTANT |
| FE-19 | `ListTrainingClosure` | `/screens/ListTrainingClosure` | [ListTrainingClosure.jsx](../G5_FE1/src/screens/pages/ListTrainingClosure.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-19 | `TrainingClosureDetail` | `/screens/TrainingClosureDetail/{id}` | [TrainingClosureDetail.jsx](../G5_FE1/src/screens/pages/TrainingClosureDetail.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-19 | `TrainingClosureDetail_Ready` | `/screens/TrainingClosureDetail_Ready/{id}` | [TrainingClosureDetail_Ready.jsx](../G5_FE1/src/screens/pages/TrainingClosureDetail_Ready.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-19 | `TrainingClosureDetail_Withdrawal` | `/screens/TrainingClosureDetail_Withdrawal/{id}` | [TrainingClosureDetail_Withdrawal.jsx](../G5_FE1/src/screens/pages/TrainingClosureDetail_Withdrawal.jsx) | ACCOUNTANT, IT_SUPPORT, MANAGER |
| FE-19 | `RecordWithdrawal` | `/screens/RecordWithdrawal/{id}` | [RecordWithdrawal.jsx](../G5_FE1/src/screens/pages/RecordWithdrawal.jsx) | MANAGER |
| FE-21 | `Reports_Admin` | `/screens/Reports_Admin` | [Reports_Admin.jsx](../G5_FE1/src/screens/pages/Reports_Admin.jsx) | ADMIN |
| FE-21 | `Reports_Lecturer` | `/screens/Reports_Lecturer` | [Reports_Lecturer.jsx](../G5_FE1/src/screens/pages/Reports_Lecturer.jsx) | LECTURER |
| FE-21 | `Reports_Accountant` | `/screens/Reports_Accountant` | [Reports_Accountant.jsx](../G5_FE1/src/screens/pages/Reports_Accountant.jsx) | ACCOUNTANT |
| FE-21 | `Reports_IT_Support` | `/screens/Reports_IT_Support` | [Reports_IT_Support.jsx](../G5_FE1/src/screens/pages/Reports_IT_Support.jsx) | IT_SUPPORT |
| FE-21 | `Reports_Student_Manager` | `/screens/Reports_Student_Manager` | [Reports_Student_Manager.jsx](../G5_FE1/src/screens/pages/Reports_Student_Manager.jsx) | MANAGER |
| FE-21 | `Reports_Admissions` | `/screens/Reports_Admissions` | [Reports_Admissions.jsx](../G5_FE1/src/screens/pages/Reports_Admissions.jsx) | HR |
| FE-09 | `ListClass_Lecturer` | `/screens/ListClass_Lecturer` | [ListClass_Lecturer.jsx](../G5_FE1/src/screens/pages/ListClass_Lecturer.jsx) | LECTURER |
| FE-09 | `ClassDetail_Lecturer` | `/screens/ClassDetail_Lecturer/{id}` | [ClassDetail_Lecturer.jsx](../G5_FE1/src/screens/pages/ClassDetail_Lecturer.jsx) | LECTURER |
| FE-05 | `StudentProfileDetail_Student` | `/screens/StudentProfileDetail_Student` | [StudentProfileDetail_Student.jsx](../G5_FE1/src/screens/pages/StudentProfileDetail_Student.jsx) | STUDENT |
| FE-02 | `UserProfile_AdminView` | `/admin/users/{id}/view` | [UserDetailPage.jsx](../G5_FE1/src/admin/UserDetailPage.jsx) | ADMIN |

`EditUser` sử dụng thêm `/admin/users/new` và `UserCreatePage.jsx` để tạo tài khoản. Với `ClassGradebook`, ID là lớp; với `ClassAttendance`/`Attendance_Correction`, ID là buổi học; với các màn đóng hồ sơ, ID là ghi danh; với receipt/thu hồi, ID là lượt bàn giao. Các trang chi tiết chưa chọn ID có thể mở danh sách để chọn hồ sơ trong phạm vi.

Số màn theo quyền hiện tại (tính cả trang công khai, xác thực chung và các màn bổ sung): Admin 35, Lecturer 29, Student 26, Accountant 41, IT Support 44, Manager 50, HR 24. Quyền ngành nghề và khóa học chỉ dành cho Admin; quyền ĐX chưa mặc định bật.

## Danh mục dùng chung bổ sung

Chi tiết cấu hình và thao tác: [MASTER_DATA.md](MASTER_DATA.md). Các màn này dùng migration V11, tách khỏi ma trận SVG ban đầu.

| FE | Mã màn | Route | File giao diện | Vai trò mặc định |
| --- | --- | --- | --- | --- |
| MD-01 | `ListEquipmentGroup` | `/screens/ListEquipmentGroup` | [ListEquipmentGroup.jsx](../G5_FE1/src/screens/pages/ListEquipmentGroup.jsx) | IT_SUPPORT |
| MD-01 | `AddEquipmentGroup` | `/screens/AddEquipmentGroup` | [AddEquipmentGroup.jsx](../G5_FE1/src/screens/pages/AddEquipmentGroup.jsx) | IT_SUPPORT |
| MD-01 | `EditEquipmentGroup` | `/screens/EditEquipmentGroup/{id}` | [EditEquipmentGroup.jsx](../G5_FE1/src/screens/pages/EditEquipmentGroup.jsx) | IT_SUPPORT |
| MD-02 | `ListStorageLocation` | `/screens/ListStorageLocation` | [ListStorageLocation.jsx](../G5_FE1/src/screens/pages/ListStorageLocation.jsx) | IT_SUPPORT |
| MD-02 | `AddStorageLocation` | `/screens/AddStorageLocation` | [AddStorageLocation.jsx](../G5_FE1/src/screens/pages/AddStorageLocation.jsx) | IT_SUPPORT |
| MD-02 | `EditStorageLocation` | `/screens/EditStorageLocation/{id}` | [EditStorageLocation.jsx](../G5_FE1/src/screens/pages/EditStorageLocation.jsx) | IT_SUPPORT |
| MD-03 | `ListProfession` | `/screens/ListProfession` | [ListProfession.jsx](../G5_FE1/src/screens/pages/ListProfession.jsx) | ADMIN |
| MD-03 | `AddProfession` | `/screens/AddProfession` | [AddProfession.jsx](../G5_FE1/src/screens/pages/AddProfession.jsx) | ADMIN |
| MD-03 | `EditProfession` | `/screens/EditProfession/{id}` | [EditProfession.jsx](../G5_FE1/src/screens/pages/EditProfession.jsx) | ADMIN |
| MD-04 | `ListClassroom` | `/screens/ListClassroom` | [ListClassroom.jsx](../G5_FE1/src/screens/pages/ListClassroom.jsx) | ADMIN |
| MD-04 | `AddClassroom` | `/screens/AddClassroom` | [AddClassroom.jsx](../G5_FE1/src/screens/pages/AddClassroom.jsx) | ADMIN |
| MD-04 | `EditClassroom` | `/screens/EditClassroom/{id}` | [EditClassroom.jsx](../G5_FE1/src/screens/pages/EditClassroom.jsx) | ADMIN |

## Cấp tài khoản học viên bổ sung

| FE | Mã màn | Route | File giao diện | Vai trò mặc định |
| --- | --- | --- | --- | --- |
| FE-06A | `ListStudentAccount` | `/screens/ListStudentAccount` | [ListStudentAccount.jsx](../G5_FE1/src/screens/pages/ListStudentAccount.jsx) | MANAGER |

V14 cấp quyền riêng cho Manager; metadata bổ sung nằm trong `docs/workspace-screen-extensions.json`. Hướng dẫn và API: [STUDENT_ACCOUNT_PROVISIONING.md](STUDENT_ACCOUNT_PROVISIONING.md).
