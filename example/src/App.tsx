import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { StatusBar } from 'react-native';
import { demos, type RootStackParamList } from './screens';
import HomeScreen from './screens/HomeScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <>
      <StatusBar barStyle="dark-content" />
      <NavigationContainer>
        <Stack.Navigator
          initialRouteName="Home"
          screenOptions={{
            headerStyle: { backgroundColor: '#f8fafc' },
            headerTitleStyle: { color: '#0e1116', fontWeight: '700' },
            headerShadowVisible: false,
            headerTintColor: '#3b82f6',
            contentStyle: { backgroundColor: '#f8fafc' },
          }}
        >
          <Stack.Screen
            name="Home"
            component={HomeScreen}
            options={{ title: 'Adaptive Text' }}
          />
          {demos.map((demo) => (
            <Stack.Screen
              key={demo.name}
              name={demo.name as keyof RootStackParamList}
              component={demo.component}
              options={{ title: demo.title, ...demo.options }}
            />
          ))}
        </Stack.Navigator>
      </NavigationContainer>
    </>
  );
}
