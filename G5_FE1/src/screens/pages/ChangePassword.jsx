import UserProfile from '../../UserProfile';
export default function ChangePassword({onNavigate,...props}){return <UserProfile {...props} passwordOnly onBack={()=>onNavigate('/profile')}/>;}
